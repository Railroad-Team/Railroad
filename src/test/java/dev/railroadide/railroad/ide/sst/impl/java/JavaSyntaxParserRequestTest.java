package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.*;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTreeDumper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class JavaSyntaxParserRequestTest {
    @Test
    public void activeEmptyRequestPreservesLegacyStructureAndExactSnapshot() {
        assertEquivalentParse("");
    }

    @Test
    public void activeRequestPreservesDeclarationsAndTrivia() {
        assertEquivalentParse("package demo; /* retain trivia */ class A { int x = 1; }");
    }

    @Test
    public void activeRequestPreservesMalformedInputAndRecovery() {
        assertEquivalentParse("class Broken { void run( { int x = ; }");
    }

    @Test
    public void activeRequestPreservesLambdaLookaheadBehavior() {
        assertEquivalentParse(
            "class A { void run() { java.util.function.Function<Integer, Integer> f = (x) -> x + 1; } }");
    }

    @Test
    public void activeRequestPreservesEnhancedForLookaheadBehavior() {
        assertEquivalentParse("class A { void run() { for (String s : items) { use(s); } } }");
    }

    private static void assertEquivalentParse(String input) {
        var document = text(input, "java");
        var context = context(document, CancellationToken.none(), Deadline.none());

        SyntaxTree actual = JavaSyntaxParser.parse(context);
        SyntaxTree legacy = JavaSyntaxParser.parse(document);

        assertSame(document, actual.documentSnapshot());
        assertEquals(SyntaxTreeDumper.dump(legacy), SyntaxTreeDumper.dump(actual));
        assertEquals(input, JavaParserTestSupport.syntaxText(actual));
    }

    @Test
    public void nullContextIsRejected() {
        assertThrows(NullPointerException.class, () -> JavaSyntaxParser.parse((FeatureRequestContext) null));
    }

    @Test
    public void binaryAndNonJavaInputsAreRejected() {
        DocumentId id = DocumentId.create();
        var binary = new BinaryDocumentSnapshot(id, DocumentUri.inMemory(id), DocumentVersion.initial(),
            "java", new byte[]{1, 2, 3});
        var binaryRequest = context(binary, CancellationToken.none(), Deadline.none());
        var otherLanguageRequest = context(text("class A {}", "other-language"),
            CancellationToken.none(), Deadline.none());

        assertThrows(IllegalArgumentException.class, () -> JavaSyntaxParser.parse(binaryRequest));
        assertThrows(IllegalArgumentException.class, () -> JavaSyntaxParser.parse(otherLanguageRequest));
    }

    @Test
    public void preCancelledRequestPropagatesItsOriginalFailureBeforeInputValidation() {
        var failure = new CancellationException("owner cancelled");
        CancellationToken token = new CancellationToken() {
            @Override
            public boolean isCancellationRequested() {
                return true;
            }

            @Override
            public void throwIfCancellationRequested() {
                throw failure;
            }
        };
        var context = context(text("", "other-language"), token, Deadline.after(Duration.ZERO, () -> 0L));

        assertSame(failure, assertThrows(CancellationException.class, () -> JavaSyntaxParser.parse(context)));
    }

    @Test
    public void preExpiredRequestDoesNotReturnATree() {
        var context = context(text("class A {}", "java"), CancellationToken.none(),
            Deadline.after(Duration.ZERO, () -> 0L));

        assertThrows(DeadlineExceededException.class, () -> JavaSyntaxParser.parse(context));
    }

    @Test
    public void requestCancellationIsObservedDuringParsingRatherThanOnlyAtTheBoundary() {
        var observations = new AtomicInteger();
        CancellationToken token = () -> observations.incrementAndGet() >= 64;
        var context = context(text("class A { " + "int field; ".repeat(1000) + "}", "java"),
            token, Deadline.none());

        assertThrows(CancellationException.class, () -> JavaSyntaxParser.parse(context));
        assertTrue(observations.get() >= 64);
    }

    @Test
    public void originalDeadlineIsObservedDuringParsingWithoutResettingTheBudget() {
        var clock = new AtomicLong();
        Deadline deadline = Deadline.after(Duration.ofNanos(64), clock::getAndIncrement);
        var context = context(text("class A { " + "int field; ".repeat(1000) + "}", "java"),
            CancellationToken.none(), deadline);

        assertThrows(DeadlineExceededException.class, () -> JavaSyntaxParser.parse(context));
        assertSame(deadline, context.deadline());
    }

    private static TextDocumentSnapshot text(String input, String languageId) {
        DocumentId id = DocumentId.create();
        return new TextDocumentSnapshot(id, DocumentUri.inMemory(id), new DocumentVersion(7), languageId,
            input, StandardCharsets.UTF_16LE);
    }

    private static FeatureRequestContext context(
        DocumentSnapshot document,
        CancellationToken token,
        Deadline deadline
    ) {
        var workspace = new WorkspaceSnapshot("test-project", Map.of(document.id(), document),
            new WorkspaceConfigurationSnapshot(0, Set.of()), 1);
        return new FeatureRequestContext(workspace, document, token, deadline);
    }
}
