package dev.railroadide.railroad.ide.sst.request.api;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.ui.codeeditor.SyntaxHighlightingProvider;
import org.fxmisc.richtext.model.StyleSpans;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class SyntaxHighlightingProviderRequestTest {
    private final DocumentId id = DocumentId.create();
    private final TextDocumentSnapshot document = new TextDocumentSnapshot(
        id, DocumentUri.inMemory(id), DocumentVersion.initial(), "independent-text-provider",
        "original text", StandardCharsets.UTF_8);

    @Test
    public void existingLambdaReceivesCapturedTextAndRetainsItsSpansAndRequestIdentity() {
        FeatureRequestContext context = context(document, CancellationToken.none(), Deadline.none());
        StyleSpans<Collection<String>> spans = styles(document.text());
        var calls = new AtomicInteger();
        SyntaxHighlightingProvider provider = text -> {
            calls.incrementAndGet();
            assertSame(document.text(), text);
            return spans;
        };

        var result = provider.compute(context);

        assertEquals(1, calls.get());
        assertSame(context, result.context());
        assertTrue(result.belongsTo(context));
        assertSame(spans, result.value());
    }

    @Test
    public void nullContextIsRejectedBeforeProviderWork() {
        var calls = new AtomicInteger();
        SyntaxHighlightingProvider provider = text -> {
            calls.incrementAndGet();
            return styles(text);
        };

        assertThrows(NullPointerException.class, () -> provider.compute((FeatureRequestContext) null));
        assertEquals(0, calls.get());
    }

    @Test
    public void binaryInputIsRejectedBeforeProviderWork() {
        var binary = new BinaryDocumentSnapshot(id, document.uri(), document.version(),
            "independent-binary-provider", new byte[]{1, 2, 3});
        FeatureRequestContext context = context(binary, CancellationToken.none(), Deadline.none());
        var calls = new AtomicInteger();
        SyntaxHighlightingProvider provider = text -> {
            calls.incrementAndGet();
            return styles(text);
        };

        assertThrows(IllegalArgumentException.class, () -> provider.compute(context));
        assertEquals(0, calls.get());
    }

    @Test
    public void cancellationBeforeDispatchSkipsProviderWork() {
        var source = new CancellationTokenSource();
        source.cancel();
        FeatureRequestContext context = context(document, source.token(), Deadline.none());
        var calls = new AtomicInteger();
        SyntaxHighlightingProvider provider = text -> {
            calls.incrementAndGet();
            return styles(text);
        };

        assertThrows(CancellationException.class, () -> provider.compute(context));
        assertEquals(0, calls.get());
    }

    @Test
    public void expiryBeforeDispatchSkipsProviderWork() {
        var clock = new AtomicLong(100);
        FeatureRequestContext context = context(document, CancellationToken.none(),
            Deadline.after(Duration.ZERO, clock::get));
        var calls = new AtomicInteger();
        SyntaxHighlightingProvider provider = text -> {
            calls.incrementAndGet();
            return styles(text);
        };

        assertThrows(DeadlineExceededException.class, () -> provider.compute(context));
        assertEquals(0, calls.get());
    }

    @Test
    public void cancellationDuringComputationRejectsTheComputedStyles() {
        var source = new CancellationTokenSource();
        FeatureRequestContext context = context(document, source.token(), Deadline.none());
        var calls = new AtomicInteger();
        SyntaxHighlightingProvider provider = text -> {
            calls.incrementAndGet();
            source.cancel();
            return styles(text);
        };

        assertThrows(CancellationException.class, () -> provider.compute(context));
        assertEquals(1, calls.get());
    }

    @Test
    public void expiryDuringComputationRejectsTheComputedStyles() {
        var clock = new AtomicLong(100);
        FeatureRequestContext context = context(document, CancellationToken.none(),
            Deadline.after(Duration.ofNanos(50), clock::get));
        var calls = new AtomicInteger();
        SyntaxHighlightingProvider provider = text -> {
            calls.incrementAndGet();
            clock.set(150);
            return styles(text);
        };

        assertThrows(DeadlineExceededException.class, () -> provider.compute(context));
        assertEquals(1, calls.get());
    }

    @Test
    public void providerFailurePropagatesUnchangedEvenIfCancellationAlsoArrives() {
        var source = new CancellationTokenSource();
        FeatureRequestContext context = context(document, source.token(), Deadline.none());
        var failure = new IllegalStateException("Provider failed");
        SyntaxHighlightingProvider provider = text -> {
            source.cancel();
            throw failure;
        };

        assertSame(failure, assertThrows(IllegalStateException.class, () -> provider.compute(context)));
    }

    @Test
    public void providerCancellationPropagatesUnchanged() {
        FeatureRequestContext context = context(document, CancellationToken.none(), Deadline.none());
        var failure = new CancellationException("Provider cancelled");
        SyntaxHighlightingProvider provider = text -> {
            throw failure;
        };

        assertSame(failure, assertThrows(CancellationException.class, () -> provider.compute(context)));
    }

    @Test
    public void historicalAndNewerRequestsUseTheirOwnCapturedText() {
        FeatureRequestContext old = context(document, CancellationToken.none(), Deadline.none());
        var newer = new TextDocumentSnapshot(id, document.uri(), document.version().next(),
            document.languageId(), "newer and longer text", document.encoding());
        FeatureRequestContext current = context(newer, CancellationToken.none(), Deadline.none());
        SyntaxHighlightingProvider provider = SyntaxHighlightingProviderRequestTest::styles;

        var currentResult = provider.compute(current);
        var oldResult = provider.compute(old);

        assertSame(current, currentResult.context());
        assertSame(old, oldResult.context());
        assertEquals(newer.text().length(), currentResult.value().length());
        assertEquals(document.text().length(), oldResult.value().length());
        assertFalse(oldResult.belongsTo(current));
    }

    @Test
    public void emptyTextIsAValidHighlightingInput() {
        var empty = new TextDocumentSnapshot(id, document.uri(), document.version(), document.languageId(),
            "", document.encoding());
        FeatureRequestContext context = context(empty, CancellationToken.none(), Deadline.none());
        SyntaxHighlightingProvider provider = SyntaxHighlightingProviderRequestTest::styles;

        var result = provider.compute(context);

        assertSame(context, result.context());
        assertEquals(0, result.value().length());
    }

    @Test
    public void explicitCancellationTakesPrecedenceOverDeadlineExpiry() {
        var source = new CancellationTokenSource();
        source.cancel();
        var clock = new AtomicLong(100);
        FeatureRequestContext context = context(document, source.token(), Deadline.after(Duration.ZERO, clock::get));
        SyntaxHighlightingProvider provider = text -> {
            throw new AssertionError("Inactive request must not run");
        };

        var failure = assertThrows(CancellationException.class, () -> provider.compute(context));

        assertEquals(CancellationException.class, failure.getClass());
    }

    private static StyleSpans<Collection<String>> styles(String text) {
        return StyleSpans.singleton(List.of("highlighted"), text.length());
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
