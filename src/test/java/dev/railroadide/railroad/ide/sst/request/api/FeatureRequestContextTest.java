package dev.railroadide.railroad.ide.sst.request.api;

import dev.railroadide.railroad.ide.sst.document.api.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

public class FeatureRequestContextTest {
    private final DocumentId id = DocumentId.create();
    private final TextDocumentSnapshot document = new TextDocumentSnapshot(
        id,
        DocumentUri.inMemory(id),
        DocumentVersion.initial(),
        "unregistered-text-provider",
        "original text",
        StandardCharsets.UTF_8);
    private final WorkspaceSnapshot workspace = workspace(document, 1);

    @Test
    public void activeTextRequestRetainsItsExactInputsAndControls() {
        var source = new CancellationTokenSource();
        Deadline deadline = Deadline.none();
        var context = new FeatureRequestContext(workspace, document, source.token(), deadline);

        assertSame(workspace, context.workspaceSnapshot());
        assertSame(document, context.documentSnapshot());
        assertSame(source.token(), context.cancellationToken());
        assertSame(deadline, context.deadline());
        assertDoesNotThrow(context::checkActive);
    }

    @Test
    public void binaryDocumentsUseTheSameRequestContract() {
        var binary = new BinaryDocumentSnapshot(id, document.uri(), document.version(),
            "unregistered-binary-provider", new byte[]{1, 2, 3});
        WorkspaceSnapshot binaryWorkspace = workspace(binary, 1);
        var context = new FeatureRequestContext(binaryWorkspace, binary, CancellationToken.none(), Deadline.none());

        assertSame(binaryWorkspace, context.workspaceSnapshot());
        assertSame(binary, context.documentSnapshot());
        assertArrayEquals(new byte[]{1, 2, 3}, binary.copyBytes());
        assertDoesNotThrow(context::checkActive);
    }

    @Test
    public void nullComponentsAreRejected() {
        assertThrows(NullPointerException.class,
            () -> new FeatureRequestContext(null, document, CancellationToken.none(), Deadline.none()));
        assertThrows(NullPointerException.class,
            () -> new FeatureRequestContext(workspace, null, CancellationToken.none(), Deadline.none()));
        assertThrows(NullPointerException.class,
            () -> new FeatureRequestContext(workspace, document, null, Deadline.none()));
        assertThrows(NullPointerException.class,
            () -> new FeatureRequestContext(workspace, document, CancellationToken.none(), null));
    }

    @Test
    public void documentsAbsentFromTheWorkspaceAreRejected() {
        var empty = new WorkspaceSnapshot(workspace.projectId(), Map.of(), workspace.configuration(), 2);

        assertThrows(IllegalArgumentException.class,
            () -> new FeatureRequestContext(empty, document, CancellationToken.none(), Deadline.none()));
    }

    @Test
    public void differentContentRevisionOfTheSameDocumentIsRejected() {
        var newer = new TextDocumentSnapshot(id, document.uri(), document.version().next(), document.languageId(),
            "edited text", document.encoding());

        assertThrows(IllegalArgumentException.class,
            () -> new FeatureRequestContext(workspace, newer, CancellationToken.none(), Deadline.none()));
    }

    @Test
    public void independentlyCapturedEquivalentSnapshotIsRejected() {
        var equivalent = new TextDocumentSnapshot(id, document.uri(), document.version(), document.languageId(),
            document.text(), document.encoding());

        assertTrue(document.hasSameRevision(equivalent));
        assertThrows(IllegalArgumentException.class,
            () -> new FeatureRequestContext(workspace, equivalent, CancellationToken.none(), Deadline.none()));
    }

    @Test
    public void metadataChangesAtTheSameContentRevisionAreRejected() {
        var renamed = new TextDocumentSnapshot(id, DocumentUri.virtual("memory", "renamed.txt"), document.version(),
            document.languageId(), document.text(), document.encoding());
        var reencoded = new TextDocumentSnapshot(id, document.uri(), document.version(), document.languageId(),
            document.text(), StandardCharsets.UTF_16);

        assertTrue(document.hasSameRevision(renamed));
        assertTrue(document.hasSameRevision(reencoded));
        assertThrows(IllegalArgumentException.class,
            () -> new FeatureRequestContext(workspace, renamed, CancellationToken.none(), Deadline.none()));
        assertThrows(IllegalArgumentException.class,
            () -> new FeatureRequestContext(workspace, reencoded, CancellationToken.none(), Deadline.none()));
    }

    @Test
    public void alreadyCancelledRequestsCanBeConstructedAndInspected() {
        var source = new CancellationTokenSource();
        source.cancel();

        var context = new FeatureRequestContext(workspace, document, source.token(), Deadline.none());

        assertThrows(CancellationException.class, context::checkActive);
        assertSame(workspace, context.workspaceSnapshot());
        assertSame(document, context.documentSnapshot());
        assertEquals("original text", document.text());
    }

    @Test
    public void alreadyExpiredRequestsCanBeConstructedAndInspected() {
        var clock = new AtomicLong(100);
        Deadline deadline = Deadline.after(Duration.ZERO, clock::get);

        var context = new FeatureRequestContext(workspace, document, CancellationToken.none(), deadline);

        CancellationException failure = assertThrows(DeadlineExceededException.class, context::checkActive);
        assertNotNull(failure.getMessage());
        assertFalse(failure.getMessage().isBlank());
        assertSame(workspace, context.workspaceSnapshot());
        assertSame(document, context.documentSnapshot());
    }

    @Test
    public void cancellationAfterConstructionIsObservedOnLaterChecks() {
        var source = new CancellationTokenSource();
        var context = new FeatureRequestContext(workspace, document, source.token(), Deadline.none());

        assertDoesNotThrow(context::checkActive);
        source.cancel();

        CancellationException failure = assertThrows(CancellationException.class, context::checkActive);
        assertEquals(CancellationException.class, failure.getClass());
        assertThrows(CancellationException.class, context::checkActive);
    }

    @Test
    public void deadlineExpiryIsObservedWithoutChangingTheCancellationToken() {
        var clock = new AtomicLong(100);
        Deadline deadline = Deadline.after(Duration.ofNanos(50), clock::get);
        var source = new CancellationTokenSource();
        var context = new FeatureRequestContext(workspace, document, source.token(), deadline);

        clock.set(149);
        assertDoesNotThrow(context::checkActive);

        clock.set(150);
        assertThrows(DeadlineExceededException.class, context::checkActive);
        clock.set(151);
        assertThrows(DeadlineExceededException.class, context::checkActive);

        assertFalse(source.token().isCancellationRequested());
        assertSame(document, context.documentSnapshot());
    }

    @Test
    public void explicitCancellationTakesPrecedenceOverAnExpiredDeadline() {
        var source = new CancellationTokenSource();
        source.cancel();
        var clock = new AtomicLong(100);
        var context = new FeatureRequestContext(workspace, document, source.token(),
            Deadline.after(Duration.ZERO, clock::get));

        CancellationException failure = assertThrows(CancellationException.class, context::checkActive);

        assertEquals(CancellationException.class, failure.getClass());
    }

    @Test
    public void providerCancellationExceptionIsPropagatedBeforeReadingTheDeadline() {
        var failure = new CancellationException("Provider cancelled the request");
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
                throw new AssertionError("Deadline must not be queried after explicit cancellation");
            return clock.get();
        });
        var context = new FeatureRequestContext(workspace, document, token, deadline);
        clock.set(150);

        assertSame(failure, assertThrows(CancellationException.class, context::checkActive));
    }

    @Test
    public void aNewWorkspaceCaptureDoesNotReplaceTheRequestsHistoricalInputs() {
        var context = new FeatureRequestContext(workspace, document, CancellationToken.none(), Deadline.none());
        var newer = new TextDocumentSnapshot(id, document.uri(), document.version().next(), document.languageId(),
            "edited text", document.encoding());
        WorkspaceSnapshot newerWorkspace = workspace(newer, 2);

        assertSame(newer, newerWorkspace.document(id).orElseThrow());
        assertSame(workspace, context.workspaceSnapshot());
        assertSame(document, context.documentSnapshot());
        assertEquals("original text", document.text());
        assertEquals("edited text", newer.text());
        // Activity and permission to publish against live state are separate checks.
        assertDoesNotThrow(context::checkActive);
    }

    @Test
    public void workersShareCapturedInputsAndObserveLaterCancellation() throws Exception {
        var source = new CancellationTokenSource();
        var context = new FeatureRequestContext(workspace, document, source.token(), Deadline.none());
        var checkedActive = new CountDownLatch(4);
        var cancelled = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<?>> workers = new ArrayList<>();
            for (int worker = 0; worker < 4; worker++) {
                workers.add(executor.submit(() -> {
                    try {
                        assertDoesNotThrow(context::checkActive);
                    } finally {
                        checkedActive.countDown();
                    }
                    assertTrue(cancelled.await(10, TimeUnit.SECONDS), "Owner did not cancel the request");
                    assertThrows(CancellationException.class, context::checkActive);
                    assertSame(workspace, context.workspaceSnapshot());
                    assertSame(document, context.documentSnapshot());
                    assertEquals("original text", document.text());
                    return null;
                }));
            }

            try {
                assertTrue(checkedActive.await(10, TimeUnit.SECONDS), "Workers did not check request activity");
                source.cancel();
            } finally {
                cancelled.countDown();
            }

            for (Future<?> worker : workers) {
                worker.get(10, TimeUnit.SECONDS);
            }
        }
    }

    private static WorkspaceSnapshot workspace(DocumentSnapshot document, long revision) {
        return new WorkspaceSnapshot("test-project", Map.of(document.id(), document),
            new WorkspaceConfigurationSnapshot(0, Set.of()), revision);
    }
}
