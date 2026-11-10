package dev.railroadide.railroad.ide.sst.request.api;

import dev.railroadide.railroad.ide.sst.document.api.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceRequestContextTest {
    private final WorkspaceSnapshot empty = new WorkspaceSnapshot("test-project", Map.of(),
        new WorkspaceConfigurationSnapshot(0, Set.of()), 0);

    @Test
    public void emptyWorkspaceNeedsNoDocumentAnchor() {
        var source = new CancellationTokenSource();
        Deadline deadline = Deadline.none();
        RequestContext context = new WorkspaceRequestContext(empty, source.token(), deadline);

        assertSame(empty, context.workspaceSnapshot());
        assertSame(source.token(), context.cancellationToken());
        assertSame(deadline, context.deadline());
        assertDoesNotThrow(context::checkActive);
    }

    @Test
    public void nullComponentsAreRejected() {
        assertThrows(NullPointerException.class,
            () -> new WorkspaceRequestContext(null, CancellationToken.none(), Deadline.none()));
        assertThrows(NullPointerException.class,
            () -> new WorkspaceRequestContext(empty, null, Deadline.none()));
        assertThrows(NullPointerException.class,
            () -> new WorkspaceRequestContext(empty, CancellationToken.none(), null));
    }

    @Test
    public void workspaceCanContainTextAndBinaryInputsWithoutSelectingEither() {
        DocumentId textId = DocumentId.create();
        DocumentId binaryId = DocumentId.create();
        var text = new TextDocumentSnapshot(textId, DocumentUri.inMemory(textId), DocumentVersion.initial(),
            "text-provider", "source", StandardCharsets.UTF_8);
        var binary = new BinaryDocumentSnapshot(binaryId, DocumentUri.inMemory(binaryId), DocumentVersion.initial(),
            "binary-provider", new byte[]{1, 2, 3});
        var workspace = new WorkspaceSnapshot(empty.projectId(), Map.of(textId, text, binaryId, binary),
            empty.configuration(), 1);
        RequestContext context = new WorkspaceRequestContext(workspace, CancellationToken.none(), Deadline.none());

        assertSame(text, context.workspaceSnapshot().document(textId).orElseThrow());
        assertSame(binary, context.workspaceSnapshot().document(binaryId).orElseThrow());
        assertDoesNotThrow(context::checkActive);
    }

    @Test
    public void alreadyCancelledRequestRetainsItsHistoricalWorkspace() {
        var source = new CancellationTokenSource();
        source.cancel();
        RequestContext context = new WorkspaceRequestContext(empty, source.token(), Deadline.none());

        CancellationException failure = assertThrows(CancellationException.class, context::checkActive);
        assertEquals(CancellationException.class, failure.getClass());
        assertSame(empty, context.workspaceSnapshot());
    }

    @Test
    public void alreadyExpiredRequestCanBeConstructedAndInspected() {
        RequestContext context = new WorkspaceRequestContext(empty, CancellationToken.none(),
            Deadline.after(Duration.ZERO, () -> 100L));

        assertThrows(DeadlineExceededException.class, context::checkActive);
        assertSame(empty, context.workspaceSnapshot());
    }

    @Test
    public void expiryIsObservedOnLaterChecksWithoutCancellingTheToken() {
        var source = new CancellationTokenSource();
        var clock = new AtomicLong(100);
        RequestContext context = new WorkspaceRequestContext(empty, source.token(),
            Deadline.after(Duration.ofNanos(50), clock::get));

        clock.set(149);
        assertDoesNotThrow(context::checkActive);
        clock.set(150);
        assertThrows(DeadlineExceededException.class, context::checkActive);
        assertFalse(source.token().isCancellationRequested());
    }

    @Test
    public void cancellationPropagatesBeforeTheDeadlineIsRead() {
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
        var clock = new AtomicLong(100);
        Deadline deadline = Deadline.after(Duration.ofNanos(50), () -> {
            if (clock.get() != 100)
                throw new AssertionError("Cancellation must be checked before reading the deadline");
            return clock.get();
        });
        RequestContext context = new WorkspaceRequestContext(empty, token, deadline);
        clock.set(150);

        assertSame(failure, assertThrows(CancellationException.class, context::checkActive));
    }

    @Test
    public void documentWorkSharesTheOriginalBudgetAndCancellation() {
        DocumentId id = DocumentId.create();
        var document = new TextDocumentSnapshot(id, DocumentUri.inMemory(id), DocumentVersion.initial(),
            "text-provider", "source", StandardCharsets.UTF_8);
        var workspace = new WorkspaceSnapshot(empty.projectId(), Map.of(id, document), empty.configuration(), 1);
        var source = new CancellationTokenSource();
        var clock = new AtomicLong(100);
        var parent = new WorkspaceRequestContext(workspace, source.token(),
            Deadline.after(Duration.ofNanos(50), clock::get));

        clock.set(140);
        var child = new FeatureRequestContext(parent.workspaceSnapshot(), document,
            parent.cancellationToken(), parent.deadline());
        assertSame(parent.deadline(), child.deadline());
        assertDoesNotThrow(child::checkActive);

        clock.set(150);
        assertThrows(DeadlineExceededException.class, parent::checkActive);
        assertThrows(DeadlineExceededException.class, child::checkActive);

        source.cancel();
        assertEquals(CancellationException.class,
            assertThrows(CancellationException.class, parent::checkActive).getClass());
        assertEquals(CancellationException.class,
            assertThrows(CancellationException.class, child::checkActive).getClass());
    }

    @Test
    public void workerObservesOwnerCancellationThroughTheSharedContract() throws Exception {
        var source = new CancellationTokenSource();
        RequestContext context = new WorkspaceRequestContext(empty, source.token(), Deadline.none());
        try (var executor = Executors.newSingleThreadExecutor()) {
            executor.submit(() -> {
                assertSame(empty, context.workspaceSnapshot());
                assertDoesNotThrow(context::checkActive);
            }).get(10, TimeUnit.SECONDS);

            source.cancel();

            executor.submit(() -> {
                assertSame(empty, context.workspaceSnapshot());
                assertThrows(CancellationException.class, context::checkActive);
            }).get(10, TimeUnit.SECONDS);
        }
    }
}
