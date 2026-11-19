package dev.railroadide.railroad.ide.sst.syntax.internal;

import dev.railroadide.railroad.ide.sst.document.api.*;
import dev.railroadide.railroad.ide.sst.request.api.Deadline;
import dev.railroadide.railroad.ide.sst.request.api.DeadlineExceededException;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxKind;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTree;
import dev.railroadide.railroad.ide.sst.syntax.api.SyntaxTreeDumper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class SyntaxValidationCancellationTest {
    @Test
    public void cancelledValidationOfWarmChildrenCanBeRetried() {
        RedNode root = RedFactory.root(greenRoot());
        var cached = root.children();
        var failure = new CancellationException("cancel validation");
        var checks = new AtomicInteger();

        assertSame(failure, assertThrows(CancellationException.class, () -> SyntaxTreeValidator.validate(root, () -> {
            if (checks.incrementAndGet() >= 32)
                throw failure;
        })));

        assertTrue(checks.get() >= 32, "Cancellation must occur within traversal, not only at entry");
        assertSame(cached, root.children());
        assertDoesNotThrow(() -> SyntaxTreeValidator.validate(root));
    }

    @Test
    public void deadlineExpiryPropagatesDuringChildConstructionAndAllowsRetry() {
        RedNode root = RedFactory.root(greenRoot());
        var clock = new AtomicLong();
        Deadline deadline = Deadline.after(Duration.ofNanos(32), clock::getAndIncrement);

        assertThrows(DeadlineExceededException.class, () -> SyntaxTreeValidator.validate(root, () -> {
            if (deadline.isExpired())
                throw new DeadlineExceededException();
        }));

        assertEquals(128, root.children().size());
        assertDoesNotThrow(() -> SyntaxTreeValidator.validate(root));
    }

    @Test
    public void factoryPropagatesCancellationDuringValidationInsteadOfReturningATree() {
        var failure = new CancellationException("cancel factory validation");
        var checks = new AtomicInteger();
        TextDocumentSnapshot document = document();
        GreenNode green = greenRoot();

        assertSame(failure, assertThrows(CancellationException.class,
            () -> SyntaxInternalFactory.treeFromGreenRoot(document, green, () -> {
                if (checks.incrementAndGet() >= 32)
                    throw failure;
            })));

        assertTrue(checks.get() >= 32, "The factory must pass its callback through to validation");
        SyntaxTree retried = SyntaxInternalFactory.treeFromGreenRoot(document, green);
        assertSame(document, retried.documentSnapshot());
        assertEquals(128, retried.root().children().size());
    }

    @Test
    public void activeFactoryPreservesLegacyTreeAndDoesNotRetainRequestControls() {
        TextDocumentSnapshot document = document();
        GreenNode green = greenRoot();
        var checks = new AtomicInteger();
        SyntaxTree checked = SyntaxInternalFactory.treeFromGreenRoot(document, green, checks::incrementAndGet);
        int completedChecks = checks.get();
        SyntaxTree legacy = SyntaxInternalFactory.treeFromGreenRoot(document, green);

        assertTrue(completedChecks > 1);
        assertSame(document, checked.documentSnapshot());
        assertEquals(SyntaxTreeDumper.dump(legacy), SyntaxTreeDumper.dump(checked));
        assertDoesNotThrow(() -> SyntaxTreeValidator.validate(checked.root()));
        assertEquals(completedChecks, checks.get(), "Later tree reads must not call a completed request's callback");
    }

    @Test
    public void validationStillRejectsInvalidRootPositions() {
        var invalid = new RedNode(greenRoot(), null, 1);

        assertThrows(IllegalStateException.class, () -> SyntaxTreeValidator.validate(invalid));
        assertThrows(IllegalStateException.class, () -> SyntaxTreeValidator.validate(invalid, () -> {
        }));
    }

    private static GreenNode greenRoot() {
        return GreenNode.root(Collections.nCopies(128, new GreenToken(SyntaxKind.TOKEN, "x")));
    }

    private static TextDocumentSnapshot document() {
        DocumentId id = DocumentId.create();
        return new TextDocumentSnapshot(id, DocumentUri.inMemory(id), DocumentVersion.initial(), "test-language",
            "x".repeat(128), StandardCharsets.UTF_8);
    }
}
