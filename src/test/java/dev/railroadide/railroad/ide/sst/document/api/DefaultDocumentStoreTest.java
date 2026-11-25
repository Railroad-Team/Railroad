package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultDocumentStoreTest {
    private final WorkspaceConfigurationSnapshot initialConfiguration = new WorkspaceConfigurationSnapshot(0, Set.of());
    private final DefaultDocumentStore store = new DefaultDocumentStore("project-a", initialConfiguration);
    private final DocumentId id = DocumentId.create();

    @Test
    public void delayedDiskReadCannotOverwriteACommittedOverlay() {
        store.publishBacking(snapshot(new DocumentVersion(1), "original"));
        store.publishOverlay(snapshot(new DocumentVersion(2), "saved"));
        DocumentState observed = store.state(id).orElseThrow();
        store.commitOverlay(id, observed.backing().version(), observed.overlay().version());
        WorkspaceSnapshot saved = store.capture();
        assertThrows(IllegalStateException.class,
            () -> store.publishBacking(snapshot(new DocumentVersion(3), "original"), observed));
        assertEquals(saved, store.capture());
    }

    @Test
    public void abandonedOverlayRevisionCannotBeReusedForDifferentContent() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(1), "backing");
        TextDocumentSnapshot draft = snapshot(new DocumentVersion(2), "draft");
        store.publishBacking(backing);
        store.publishOverlay(draft);
        WorkspaceSnapshot retained = store.capture();
        store.removeOverlay(id, draft.version());
        WorkspaceSnapshot before = store.capture();
        assertThrows(IllegalArgumentException.class,
            () -> store.publishOverlay(snapshot(draft.version(), "different")));
        assertThrows(IllegalArgumentException.class,
            () -> store.publishBacking(snapshot(draft.version(), "different")));
        assertEquals(before, store.capture());
        store.publishOverlay(snapshot(draft.version(), draft.text()));
        assertTrue(retained.document(id).orElseThrow().hasSameRevision(store.current(id).orElseThrow()));
    }

    @Test
    public void overlayMayAdvanceToUnusedRevisionBelowExternalBacking() {
        store.publishBacking(snapshot(new DocumentVersion(1), "original"));
        store.publishOverlay(snapshot(new DocumentVersion(2), "draft"));
        store.publishBacking(snapshot(new DocumentVersion(5), "external"));
        store.publishOverlay(snapshot(new DocumentVersion(3), "next edit"));
        assertTrue(store.state(id).orElseThrow().isStale());
        assertEquals(new DocumentVersion(3), store.current(id).orElseThrow().version());
    }

    @Test
    public void revisionHistoryDistinguishesExactUtf16AndSnapshotKind() {
        store.publishOverlay(snapshot(new DocumentVersion(2), "\uD800"));
        store.removeOverlay(id, new DocumentVersion(2));
        assertThrows(IllegalArgumentException.class,
            () -> store.publishOverlay(snapshot(new DocumentVersion(2), "?")));
        assertThrows(IllegalArgumentException.class,
            () -> store.publishBacking(new BinaryDocumentSnapshot(id, DocumentUri.inMemory(id),
                new DocumentVersion(2), "binary", new byte[]{63})));
    }

    @Test
    public void saveAsAtomicallyCommitsStaleOverlayAndNotifiesOnce() {
        store.publishBacking(snapshot(new DocumentVersion(1), "original"));
        TextDocumentSnapshot local = snapshot(new DocumentVersion(2), "local");
        store.publishOverlay(local);
        store.publishBacking(snapshot(new DocumentVersion(3), "external"));
        DocumentState expected = store.state(id).orElseThrow();
        WorkspaceSnapshot before = store.capture();
        var notifications = new AtomicInteger();
        DocumentStore.ChangeSubscription subscription = store.onChange(notifications::incrementAndGet);
        DocumentUri target = DocumentUri.parse("memory:///saved.java");
        var saved = new TextDocumentSnapshot(id, target, new DocumentVersion(4), "java", "local",
            StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class, () -> store.commitSaveAs(local, expected));
        assertEquals(before, store.capture());
        assertEquals(0, notifications.get());
        store.commitSaveAs(saved, expected);
        assertSame(saved, store.current(id).orElseThrow());
        assertNull(store.state(id).orElseThrow().overlay());
        assertEquals(before.revision() + 1, store.capture().revision());
        assertEquals(1, notifications.get());
        assertThrows(IllegalStateException.class, () -> store.commitSaveAs(saved, expected));
        assertEquals(1, notifications.get());
        subscription.close();
        store.publishConfiguration(new WorkspaceConfigurationSnapshot(1, Set.of()));
        assertEquals(1, notifications.get());
    }

    @Test
    public void saveAsWithoutBackingRemovesOverlayInOneTransition() {
        TextDocumentSnapshot local = snapshot(new DocumentVersion(2), "local");
        store.publishOverlay(local);
        DocumentState expected = store.state(id).orElseThrow();
        WorkspaceSnapshot before = store.capture();
        store.commitSaveAs(local, expected);
        assertSame(local, store.current(id).orElseThrow());
        assertNull(store.state(id).orElseThrow().overlay());
        assertEquals(before.revision() + 1, store.capture().revision());
        assertSame(local, before.document(id).orElseThrow());
    }

    @Test
    public void saveAsRelocatesBackingAfterNewerOverlayWasDiscarded() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(1), "saved");
        store.publishBacking(backing);
        TextDocumentSnapshot discarded = snapshot(new DocumentVersion(2), "discarded");
        store.publishOverlay(discarded);
        store.removeOverlay(id, discarded.version());
        DocumentUri target = DocumentUri.parse("memory:///saved.java");
        var saved = new TextDocumentSnapshot(id, target, backing.version(), "java", "saved",
            StandardCharsets.UTF_8);
        store.commitSaveAs(saved, store.state(id).orElseThrow());
        assertSame(saved, store.current(id).orElseThrow());
        assertEquals(discarded.version(), store.latestVersion(id).orElseThrow());
    }

    @Test
    public void removingStaleOverlayRetainsBackingAndRejectsChangedExpectedVersion() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(1), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(2), "local");
        TextDocumentSnapshot external = snapshot(new DocumentVersion(3), "external");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(external);
        WorkspaceSnapshot before = store.capture();
        assertThrows(IllegalStateException.class, () -> store.removeOverlay(id, new DocumentVersion(4)));
        assertEquals(before, store.capture());
        store.removeOverlay(id, overlay.version());
        assertSame(external, store.current(id).orElseThrow());
        assertNull(store.state(id).orElseThrow().overlay());
        assertEquals(before.revision() + 1, store.capture().revision());
        assertSame(overlay, before.document(id).orElseThrow());
    }

    @Test
    public void removingOverlayWithoutBackingRetainsBackingHistory() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(4), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(5), "local");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.removeBacking(id, backing.version());
        store.removeOverlay(id, overlay.version());
        assertTrue(store.state(id).isEmpty());
        assertTrue(store.capture().documents().isEmpty());
        assertEquals(backing.version(), store.lastBackingVersion(id).orElseThrow());
        assertThrows(IllegalArgumentException.class, () -> store.publishBacking(backing));
    }

    @Test
    public void relocatingSnapshotsAtomicallyPreservesVersionsBaseAndOldCaptures() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(1), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(2), "local");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        WorkspaceSnapshot before = store.capture();
        DocumentUri target = DocumentUri.parse("memory:///renamed.java");
        store.relocate(id, target);
        DocumentState moved = store.state(id).orElseThrow();
        assertEquals(target, moved.backing().uri());
        assertEquals(target, moved.overlay().uri());
        assertEquals(backing.version(), moved.backing().version());
        assertEquals(overlay.version(), moved.overlay().version());
        assertEquals(backing.version(), moved.baseBackingVersion());
        assertSame(overlay, before.document(id).orElseThrow());
        assertEquals(before.revision() + 1, store.capture().revision());
        WorkspaceSnapshot relocated = store.capture();
        store.relocate(id, target);
        assertEquals(relocated, store.capture());
    }

    @Test
    public void unknownDocumentReturnsEmpty() {
        assertEquals(Optional.empty(), store.current(id));
        assertEquals(Optional.empty(), store.state(id));
    }

    @Test
    public void backingOnlyReturnsBacking() {
        TextDocumentSnapshot backing = snapshot(DocumentVersion.initial(), "backing");

        store.publishBacking(backing);

        assertSame(backing, store.current(id).orElseThrow());
        DocumentState state = store.state(id).orElseThrow();
        assertSame(backing, state.backing());
        assertNull(state.overlay());
        assertNull(state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void overlayOnlyReturnsOverlay() {
        TextDocumentSnapshot overlay = snapshot(DocumentVersion.initial(), "overlay");

        store.publishOverlay(overlay);

        assertSame(overlay, store.current(id).orElseThrow());
        DocumentState state = store.state(id).orElseThrow();
        assertNull(state.backing());
        assertSame(overlay, state.overlay());
        assertNull(state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void backingAndOverlayReturnsOverlay() {
        TextDocumentSnapshot backing = snapshot(DocumentVersion.initial(), "backing");
        TextDocumentSnapshot overlay = snapshot(backing.version().next(), "overlay");

        store.publishBacking(backing);
        store.publishOverlay(overlay);

        assertSame(overlay, store.current(id).orElseThrow());
    }

    @Test
    public void replacingBackingWhileOverlayExistsStillReturnsOverlay() {
        TextDocumentSnapshot backing = snapshot(DocumentVersion.initial(), "backing");
        TextDocumentSnapshot overlay = snapshot(backing.version().next(), "overlay");
        TextDocumentSnapshot replacementBacking = snapshot(overlay.version().next(), "replacement backing");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(replacementBacking);

        assertSame(overlay, store.current(id).orElseThrow());
    }

    @Test
    public void replacingOverlayReturnsNewOverlay() {
        TextDocumentSnapshot backing = snapshot(DocumentVersion.initial(), "backing");
        TextDocumentSnapshot overlay = snapshot(backing.version().next(), "overlay");
        TextDocumentSnapshot replacementOverlay = snapshot(overlay.version().next(), "replacement overlay");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishOverlay(replacementOverlay);

        assertSame(replacementOverlay, store.current(id).orElseThrow());
    }

    @Test
    public void olderBackingIsRejectedAndOriginalSnapshotRemainsCurrent() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot olderBacking = snapshot(new DocumentVersion(3), "older backing");

        store.publishBacking(backing);

        assertThrows(IllegalArgumentException.class, () -> store.publishBacking(olderBacking));
        assertSame(backing, store.current(id).orElseThrow());
    }

    @Test
    public void olderOverlayIsRejectedAndOriginalSnapshotRemainsCurrent() {
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(5), "overlay");
        TextDocumentSnapshot olderOverlay = snapshot(new DocumentVersion(3), "older overlay");

        store.publishOverlay(overlay);

        assertThrows(IllegalArgumentException.class, () -> store.publishOverlay(olderOverlay));
        assertSame(overlay, store.current(id).orElseThrow());
    }

    @Test
    public void rejectedPublicationsPreserveOverlayAndBackingVersionGuard() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "overlay");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();

        assertThrows(IllegalArgumentException.class,
            () -> store.publishBacking(snapshot(new DocumentVersion(3), "older backing")));
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(originalState, store.state(id).orElseThrow());
        assertThrows(IllegalArgumentException.class,
            () -> store.publishOverlay(snapshot(new DocumentVersion(3), "older overlay")));
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(originalState, store.state(id).orElseThrow());
        assertThrows(IllegalArgumentException.class,
            () -> store.publishBacking(snapshot(new DocumentVersion(4), "still older backing")));
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(originalState, store.state(id).orElseThrow());
    }

    @Test
    public void newerBackingReplacesCurrentBacking() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(6), "newer backing");

        store.publishBacking(backing);
        store.publishBacking(newerBacking);

        assertSame(newerBacking, store.current(id).orElseThrow());
    }

    @Test
    public void newerOverlayReplacesCurrentOverlay() {
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(5), "overlay");
        TextDocumentSnapshot newerOverlay = snapshot(new DocumentVersion(7), "newer overlay");

        store.publishOverlay(overlay);
        store.publishOverlay(newerOverlay);

        assertSame(newerOverlay, store.current(id).orElseThrow());
    }

    @Test
    public void sameBackingVersionCanBeRepublished() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot replacement = snapshot(new DocumentVersion(5), "backing");

        store.publishBacking(backing);
        assertEquals(1L, store.capture().revision());
        store.publishBacking(backing);
        assertEquals(1L, store.capture().revision());
        assertSame(backing, store.current(id).orElseThrow());

        store.publishBacking(replacement);
        assertEquals(2L, store.capture().revision());
        assertSame(replacement, store.current(id).orElseThrow());
    }

    @Test
    public void sameOverlayVersionCanBeRepublished() {
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(5), "overlay");
        TextDocumentSnapshot replacement = snapshot(new DocumentVersion(5), "overlay");

        store.publishOverlay(overlay);
        assertEquals(1L, store.capture().revision());
        store.publishOverlay(overlay);
        assertEquals(1L, store.capture().revision());
        assertSame(overlay, store.current(id).orElseThrow());

        store.publishOverlay(replacement);
        assertEquals(2L, store.capture().revision());
        assertSame(replacement, store.current(id).orElseThrow());
    }

    @Test
    public void stateCapturesOverlayBaseAndDetectsNewerBacking() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "overlay");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(7), "external change");

        store.publishBacking(backing);
        store.publishOverlay(overlay);

        DocumentState originalState = store.state(id).orElseThrow();
        assertSame(backing, originalState.backing());
        assertSame(overlay, originalState.overlay());
        assertEquals(backing.version(), originalState.baseBackingVersion());
        assertFalse(originalState.isStale());

        store.publishBacking(snapshot(backing.version(), "backing"));
        assertFalse(store.state(id).orElseThrow().isStale());

        store.publishBacking(newerBacking);

        DocumentState state = store.state(id).orElseThrow();
        assertSame(newerBacking, state.backing());
        assertSame(overlay, state.overlay());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
        assertSame(overlay, store.current(id).orElseThrow());

        assertSame(backing, originalState.backing());
        assertSame(overlay, originalState.overlay());
        assertEquals(backing.version(), originalState.baseBackingVersion());
        assertFalse(originalState.isStale());
    }

    @Test
    public void overlayEditsAndRepublicationsPreserveOriginalBase() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "overlay");
        TextDocumentSnapshot editedOverlay = snapshot(new DocumentVersion(7), "edited overlay");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(8), "external change");
        TextDocumentSnapshot laterOverlay = snapshot(new DocumentVersion(9), "later overlay edits");

        store.publishBacking(backing);
        DocumentState backingOnly = store.state(id).orElseThrow();
        assertSame(backing, backingOnly.backing());
        assertNull(backingOnly.overlay());
        assertNull(backingOnly.baseBackingVersion());
        assertFalse(backingOnly.isStale());

        store.publishOverlay(overlay);
        DocumentState firstOverlay = store.state(id).orElseThrow();
        assertSame(backing, firstOverlay.backing());
        assertSame(overlay, firstOverlay.overlay());
        assertEquals(backing.version(), firstOverlay.baseBackingVersion());
        assertFalse(firstOverlay.isStale());
        assertSame(overlay, store.current(id).orElseThrow());

        store.publishOverlay(editedOverlay);
        DocumentState updatedOverlay = store.state(id).orElseThrow();
        assertSame(backing, updatedOverlay.backing());
        assertSame(editedOverlay, updatedOverlay.overlay());
        assertEquals(backing.version(), updatedOverlay.baseBackingVersion());
        assertFalse(updatedOverlay.isStale());
        assertSame(editedOverlay, store.current(id).orElseThrow());

        store.publishBacking(newerBacking);
        DocumentState state = store.state(id).orElseThrow();
        assertSame(newerBacking, state.backing());
        assertSame(editedOverlay, state.overlay());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
        assertSame(editedOverlay, store.current(id).orElseThrow());

        store.publishOverlay(laterOverlay);
        DocumentState laterState = store.state(id).orElseThrow();
        assertSame(newerBacking, laterState.backing());
        assertSame(laterOverlay, laterState.overlay());
        assertEquals(backing.version(), laterState.baseBackingVersion());
        assertTrue(laterState.isStale());
        assertSame(laterOverlay, store.current(id).orElseThrow());

        TextDocumentSnapshot republishedOverlay = snapshot(laterOverlay.version(), laterOverlay.text());
        store.publishOverlay(republishedOverlay);
        DocumentState republishedState = store.state(id).orElseThrow();
        assertSame(newerBacking, republishedState.backing());
        assertSame(republishedOverlay, republishedState.overlay());
        assertEquals(backing.version(), republishedState.baseBackingVersion());
        assertTrue(republishedState.isStale());
        assertSame(republishedOverlay, store.current(id).orElseThrow());
    }

    @Test
    public void overlayWithoutBackingRetainsUnknownBaseWhenBackingArrives() {
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "overlay");
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(7), "backing");
        TextDocumentSnapshot editedOverlay = snapshot(new DocumentVersion(8), "edited overlay");

        store.publishOverlay(overlay);
        DocumentState overlayOnly = store.state(id).orElseThrow();
        assertNull(overlayOnly.backing());
        assertSame(overlay, overlayOnly.overlay());
        assertNull(overlayOnly.baseBackingVersion());
        assertFalse(overlayOnly.isStale());
        assertSame(overlay, store.current(id).orElseThrow());

        store.publishBacking(backing);

        DocumentState state = store.state(id).orElseThrow();
        assertSame(backing, state.backing());
        assertSame(overlay, state.overlay());
        assertNull(state.baseBackingVersion());
        assertTrue(state.isStale());
        assertSame(overlay, store.current(id).orElseThrow());

        store.publishOverlay(editedOverlay);

        DocumentState editedState = store.state(id).orElseThrow();
        assertSame(editedOverlay, editedState.overlay());
        assertNull(editedState.baseBackingVersion());
        assertTrue(editedState.isStale());
        assertSame(editedOverlay, store.current(id).orElseThrow());
    }

    @Test
    public void reconciliationPublishesExactResolvedSnapshotAndUpdatesBase() {
        TextDocumentSnapshot originalBacking = snapshot(new DocumentVersion(5), "original backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(7), "external change");
        TextDocumentSnapshot resolved = snapshot(new DocumentVersion(8), "resolved edits");

        store.publishBacking(originalBacking);
        store.publishOverlay(overlay);
        store.publishBacking(backing);
        assertTrue(store.state(id).orElseThrow().isStale());

        store.reconcileOverlay(id, backing.version(), overlay.version(), resolved);
        assertEquals(4L, store.capture().revision());

        DocumentState state = store.state(id).orElseThrow();
        assertSame(backing, state.backing());
        assertSame(resolved, state.overlay());
        assertSame(resolved, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void reconciliationRejectsBackingChangedSinceRequestWithoutChangingState() {
        TextDocumentSnapshot originalBacking = snapshot(new DocumentVersion(5), "original backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        TextDocumentSnapshot expectedBacking = snapshot(new DocumentVersion(7), "external change");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(9), "another external change");
        TextDocumentSnapshot resolved = snapshot(new DocumentVersion(10), "resolved edits");

        store.publishBacking(originalBacking);
        store.publishOverlay(overlay);
        store.publishBacking(expectedBacking);
        store.publishBacking(newerBacking);
        DocumentState originalState = store.state(id).orElseThrow();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> store.reconcileOverlay(id, expectedBacking.version(), overlay.version(), resolved));

        assertEquals(
            "Cannot reconcile overlay for a document whose backing version has changed since reconciliation was requested.",
            exception.getMessage());
        DocumentState state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(newerBacking, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(originalBacking.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
    }

    @Test
    public void reconciliationRejectsOverlayChangedSinceRequestWithoutChangingState() {
        TextDocumentSnapshot originalBacking = snapshot(new DocumentVersion(5), "original backing");
        TextDocumentSnapshot expectedOverlay = snapshot(new DocumentVersion(6), "unsaved edits");
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(7), "external change");
        TextDocumentSnapshot newerOverlay = snapshot(new DocumentVersion(9), "more unsaved edits");
        TextDocumentSnapshot resolved = snapshot(new DocumentVersion(10), "resolved edits");

        store.publishBacking(originalBacking);
        store.publishOverlay(expectedOverlay);
        store.publishBacking(backing);
        store.publishOverlay(newerOverlay);
        DocumentState originalState = store.state(id).orElseThrow();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> store.reconcileOverlay(id, backing.version(), expectedOverlay.version(), resolved));

        assertEquals(
            "Cannot reconcile overlay for a document whose overlay version has changed since reconciliation was requested.",
            exception.getMessage());
        DocumentState state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(backing, state.backing());
        assertSame(newerOverlay, state.overlay());
        assertSame(newerOverlay, store.current(id).orElseThrow());
        assertEquals(originalBacking.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
    }

    @Test
    public void committedOverlayBecomesBackingWithoutChangingVersion() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "saved edits");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentSnapshot currentBeforeCommit = store.current(id).orElseThrow();

        store.commitOverlay(id, backing.version(), overlay.version());
        assertEquals(3L, store.capture().revision());

        DocumentState state = store.state(id).orElseThrow();
        assertSame(overlay, state.backing());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(currentBeforeCommit.version(), state.backing().version());
        assertNull(state.overlay());
        assertNull(state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void relocatedOverlayCommitRetainsExactSavedSnapshotAtNewLocation() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "edited content");
        var newUri = DocumentUri.virtual("saved", "/New.java");
        var saved = new TextDocumentSnapshot(id, newUri, overlay.version(), overlay.languageId(),
            DocumentOrigin.original(newUri), overlay.text(), overlay.encoding());
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        WorkspaceSnapshot originalWorkspace = store.capture();

        store.commitOverlay(id, backing.version(), overlay.version(), saved);

        DocumentState state = store.state(id).orElseThrow();
        assertSame(saved, state.backing());
        assertNull(state.overlay());
        assertNull(state.baseBackingVersion());
        assertFalse(state.isStale());
        assertSame(saved, store.current(id).orElseThrow());
        assertSame(saved, store.capture().document(id).orElseThrow());
        assertEquals(originalWorkspace.revision() + 1, store.capture().revision());
        assertSame(overlay, originalWorkspace.document(id).orElseThrow());

        store.removeBacking(id, saved.version());
        assertThrows(IllegalArgumentException.class, () -> store.publishBacking(saved));
    }

    @Test
    public void relocatedOverlayCommitRejectsMismatchedSavedSnapshotsWithoutChangingState() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "edited content");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        for (TextDocumentSnapshot saved : List.of(
            snapshot(overlay.version(), "different content"),
            snapshot(new DocumentVersion(5), overlay.text()),
            snapshot(new DocumentVersion(7), overlay.text()),
            snapshot(DocumentId.create(), overlay.version(), overlay.text()))) {
            assertThrows(IllegalArgumentException.class,
                () -> store.commitOverlay(id, backing.version(), overlay.version(), saved));
            DocumentState state = store.state(id).orElseThrow();
            assertEquals(originalState, state);
            assertSame(backing, state.backing());
            assertSame(overlay, state.overlay());
            assertEquals(originalWorkspace, store.capture());
        }
    }

    @Test
    public void relocatedOverlayCommitRejectsStaleOverlayWithoutChangingState() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "edited content");
        TextDocumentSnapshot updatedBacking = snapshot(new DocumentVersion(7), "external edits");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(updatedBacking);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        assertThrows(IllegalStateException.class,
            () -> store.commitOverlay(id, updatedBacking.version(), overlay.version(), overlay));

        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());
    }

    @Test
    public void savingOlderRevisionRetainsNewerOverlayAndAdvancesItsBase() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original backing");
        TextDocumentSnapshot savedRevision = snapshot(new DocumentVersion(6), "saved edits");
        TextDocumentSnapshot newerOverlay = snapshot(new DocumentVersion(7), "newer unsaved edits");

        store.publishBacking(backing);
        store.publishOverlay(savedRevision);

        store.publishOverlay(newerOverlay);

        WorkspaceSnapshot beforeAcknowledgement = store.capture();
        store.acknowledgeSavedRevision(id, backing.version(), newerOverlay.version(), savedRevision);

        DocumentState state = store.state(id).orElseThrow();
        assertAll(
            () -> assertSame(savedRevision, state.backing()),
            () -> assertSame(newerOverlay, state.overlay()),
            () -> assertSame(newerOverlay, store.current(id).orElseThrow()),
            () -> assertSame(newerOverlay, store.capture().document(id).orElseThrow()),
            () -> assertEquals(beforeAcknowledgement.revision() + 1, store.capture().revision()),
            () -> assertEquals(savedRevision.version(), state.baseBackingVersion(),
                "The retained overlay must be based on the revision that was successfully saved"),
            () -> assertFalse(state.isStale(), "Saving an older edit must not make newer edits stale"));
    }

    @Test
    public void savedAcknowledgementRejectsChangedBackingWithoutChangingState() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(7), "unsaved edits");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(6), "external change");
        TextDocumentSnapshot saved = snapshot(new DocumentVersion(6), "saved edits");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(newerBacking);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();
        assertTrue(originalState.isStale());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> store.acknowledgeSavedRevision(id, backing.version(), overlay.version(), saved));

        assertEquals(
            "Cannot acknowledge a saved revision for a document whose backing version has changed since acknowledgement was requested.",
            exception.getMessage());
        DocumentState state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(newerBacking, state.backing());
        assertSame(overlay, state.overlay());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
        assertSame(overlay, store.current(id).orElseThrow());
        assertSame(overlay, store.capture().document(id).orElseThrow());
        assertEquals(originalWorkspace.revision(), store.capture().revision());
        assertEquals(originalWorkspace, store.capture());
    }

    @Test
    public void savedAcknowledgementRejectsChangedOverlayWithoutChangingState() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(7), "unsaved edits");
        TextDocumentSnapshot newerOverlay = snapshot(new DocumentVersion(8), "newer unsaved edits");
        TextDocumentSnapshot saved = snapshot(new DocumentVersion(6), "saved edits");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishOverlay(newerOverlay);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();
        assertFalse(originalState.isStale());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> store.acknowledgeSavedRevision(id, backing.version(), overlay.version(), saved));

        assertEquals(
            "Cannot acknowledge a saved revision for a document whose overlay version has changed since acknowledgement was requested.",
            exception.getMessage());
        DocumentState state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(backing, state.backing());
        assertSame(newerOverlay, state.overlay());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertFalse(state.isStale());
        assertSame(newerOverlay, store.current(id).orElseThrow());
        assertSame(newerOverlay, store.capture().document(id).orElseThrow());
        assertEquals(originalWorkspace.revision(), store.capture().revision());
        assertEquals(originalWorkspace, store.capture());
    }

    @Test
    public void savedAcknowledgementRequiresVersionStrictlyBetweenBackingAndOverlay() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(7), "newer unsaved edits");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();
        long originalRevision = store.capture().revision();

        for (long version : List.of(4L, 5L, 7L, 8L)) {
            TextDocumentSnapshot saved = snapshot(new DocumentVersion(version), "saved edits");
            assertThrows(IllegalArgumentException.class,
                () -> store.acknowledgeSavedRevision(id, backing.version(), overlay.version(), saved));
            assertEquals(originalState, store.state(id).orElseThrow());
            assertEquals(originalRevision, store.capture().revision());
        }
    }

    @Test
    public void savedAcknowledgementRejectsDifferentDocumentIdentity() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(7), "newer unsaved edits");
        TextDocumentSnapshot saved = snapshot(DocumentId.create(), new DocumentVersion(6), "other document");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();
        long originalRevision = store.capture().revision();

        assertThrows(IllegalArgumentException.class,
            () -> store.acknowledgeSavedRevision(id, backing.version(), overlay.version(), saved));

        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(originalRevision, store.capture().revision());
    }

    @Test
    public void acknowledgedSavedRevisionRemainsInBackingVersionHistory() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original backing");
        TextDocumentSnapshot saved = snapshot(new DocumentVersion(6), "saved edits");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(7), "newer unsaved edits");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.acknowledgeSavedRevision(id, backing.version(), overlay.version(), saved);
        store.removeBacking(id, saved.version());
        DocumentState originalState = store.state(id).orElseThrow();
        long originalRevision = store.capture().revision();

        assertThrows(IllegalArgumentException.class, () -> store.publishBacking(saved));

        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(originalRevision, store.capture().revision());
    }

    @Test
    public void staleOverlayCannotBeCommittedAndStateRemainsUnchanged() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(7), "external change");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(newerBacking);
        DocumentState originalState = store.state(id).orElseThrow();
        assertTrue(originalState.isStale());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> store.commitOverlay(id, newerBacking.version(), overlay.version()));

        assertEquals("Cannot commit a stale overlay.", exception.getMessage());
        assertEquals(3L, store.capture().revision());
        DocumentState state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(newerBacking, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
    }

    @Test
    public void equalVersionOverlayCannotBeCommitted() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(5), backing.text());

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();
        assertFalse(originalState.isStale());

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> store.commitOverlay(id, backing.version(), overlay.version()));

        assertEquals("Committed overlay must have a newer version than its backing.", exception.getMessage());
        DocumentState state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(backing, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void discardingOverlayRestoresBackingContentAtNewerVersion() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "int x = 20;");
        TextDocumentSnapshot restoredBacking = snapshot(new DocumentVersion(7), backing.text());

        store.publishBacking(backing);
        store.publishOverlay(overlay);

        store.discardOverlay(id, backing.version(), overlay.version(), restoredBacking);
        assertEquals(3L, store.capture().revision());

        DocumentSnapshot current = store.current(id).orElseThrow();
        assertSame(restoredBacking, current);
        assertEquals(new DocumentVersion(7), current.version());
        assertEquals("int x = 10;", ((TextDocumentSnapshot) current).text());

        DocumentState state = store.state(id).orElseThrow();
        assertSame(restoredBacking, state.backing());
        assertNull(state.overlay());
        assertNull(state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void discardingStaleOverlayRestoresLatestBackingContentAndMetadata() {
        TextDocumentSnapshot originalBacking = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "int x = 20;");
        var updatedBacking = new TextDocumentSnapshot(
            id, DocumentUri.virtual("generated", "/Updated.java"), new DocumentVersion(7), "java",
            new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.GENERATED),
            "int x = 30;", StandardCharsets.UTF_16);
        var restoredBacking = new TextDocumentSnapshot(
            id, updatedBacking.uri(), new DocumentVersion(8), updatedBacking.languageId(),
            updatedBacking.origin(), updatedBacking.text(), updatedBacking.encoding());

        store.publishBacking(originalBacking);
        store.publishOverlay(overlay);
        store.publishBacking(updatedBacking);
        assertTrue(store.state(id).orElseThrow().isStale());

        store.discardOverlay(id, updatedBacking.version(), overlay.version(), restoredBacking);

        DocumentSnapshot current = store.current(id).orElseThrow();
        assertSame(restoredBacking, current);
        assertEquals(new DocumentVersion(8), current.version());
        assertEquals("int x = 30;", ((TextDocumentSnapshot) current).text());
        assertEquals(updatedBacking.uri(), current.uri());
        assertEquals(updatedBacking.languageId(), current.languageId());
        assertEquals(updatedBacking.origin(), current.origin());
        assertEquals(updatedBacking.encoding(), ((TextDocumentSnapshot) current).encoding());
        DocumentState state = store.state(id).orElseThrow();
        assertSame(restoredBacking, state.backing());
        assertNull(state.overlay());
        assertNull(state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void discardRejectsBackingChangedSinceRequestWithoutChangingState() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "int x = 20;");
        TextDocumentSnapshot updatedBacking = snapshot(new DocumentVersion(7), "int x = 30;");
        TextDocumentSnapshot restoredBacking = snapshot(new DocumentVersion(8), updatedBacking.text());

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(updatedBacking);
        DocumentState originalState = store.state(id).orElseThrow();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> store.discardOverlay(id, backing.version(), overlay.version(), restoredBacking));

        assertEquals(
            "Cannot discard overlay for a document whose backing version has changed since discard was requested.",
            exception.getMessage());
        DocumentState state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(updatedBacking, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
    }

    @Test
    public void discardRejectsOverlayChangedSinceRequestWithoutChangingState() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "int x = 20;");
        TextDocumentSnapshot updatedOverlay = snapshot(new DocumentVersion(7), "int x = 30;");
        TextDocumentSnapshot restoredBacking = snapshot(new DocumentVersion(8), backing.text());

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishOverlay(updatedOverlay);
        DocumentState originalState = store.state(id).orElseThrow();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> store.discardOverlay(id, backing.version(), overlay.version(), restoredBacking));

        assertEquals(
            "Cannot discard overlay for a document whose overlay version has changed since discard was requested.",
            exception.getMessage());
        DocumentState state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(backing, state.backing());
        assertSame(updatedOverlay, state.overlay());
        assertSame(updatedOverlay, store.current(id).orElseThrow());
    }

    @Test
    public void discardRejectsChangesToEachBackingMetadataFieldWithoutChangingState() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "int x = 20;");
        var restoredVersion = new DocumentVersion(7);
        List<TextDocumentSnapshot> invalidRestorations = List.of(
            new TextDocumentSnapshot(id, DocumentUri.virtual("memory", "/Other.java"), restoredVersion,
                backing.languageId(), backing.origin(), backing.text(), backing.encoding()),
            new TextDocumentSnapshot(id, backing.uri(), restoredVersion,
                "kotlin", backing.origin(), backing.text(), backing.encoding()),
            new TextDocumentSnapshot(id, backing.uri(), restoredVersion,
                backing.languageId(), DocumentOrigin.original(backing.uri()), backing.text(), backing.encoding()),
            new TextDocumentSnapshot(id, backing.uri(), restoredVersion,
                backing.languageId(), backing.origin(), backing.text(), StandardCharsets.UTF_16));

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();

        for (TextDocumentSnapshot restoredBacking : invalidRestorations) {
            assertThrows(IllegalArgumentException.class,
                () -> store.discardOverlay(id, backing.version(), overlay.version(), restoredBacking));

            DocumentState state = store.state(id).orElseThrow();
            assertEquals(originalState, state);
            assertSame(backing, state.backing());
            assertSame(overlay, state.overlay());
            assertSame(overlay, store.current(id).orElseThrow());
        }
    }

    @Test
    public void captureIncludesBothDocumentsAndPrefersOverlay() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "class Main {}");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "class Main { int x; }");
        var utilsId = DocumentId.create();
        TextDocumentSnapshot utils = snapshot(utilsId, new DocumentVersion(5), "class Utils {}");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(utils);

        WorkspaceSnapshot workspace = store.capture();

        assertEquals(2, workspace.documents().size());
        assertSame(overlay, workspace.document(id).orElseThrow());
        assertSame(utils, workspace.document(utilsId).orElseThrow());
    }

    @Test
    public void capturedWorkspaceRetainsExactSnapshotsAfterNewerPublications() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "class Main {}");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "class Main { int x; }");
        var utilsId = DocumentId.create();
        TextDocumentSnapshot utils = snapshot(utilsId, new DocumentVersion(5), "class Utils {}");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(utils);
        WorkspaceSnapshot workspace = store.capture();

        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(7), "class Main { int external; }");
        TextDocumentSnapshot newerOverlay = snapshot(new DocumentVersion(8), "class Main { int edited; }");
        TextDocumentSnapshot newerUtils = snapshot(utilsId, new DocumentVersion(6), "class Utils { int edited; }");
        store.publishBacking(newerBacking);
        store.publishOverlay(newerOverlay);
        store.publishBacking(newerUtils);
        WorkspaceSnapshot newerWorkspace = store.capture();

        assertEquals(2, newerWorkspace.documents().size());
        assertEquals(6L, newerWorkspace.revision());
        assertSame(newerOverlay, newerWorkspace.document(id).orElseThrow());
        assertSame(newerUtils, newerWorkspace.document(utilsId).orElseThrow());

        assertEquals(2, workspace.documents().size());
        assertEquals(3L, workspace.revision());
        assertSame(overlay, workspace.document(id).orElseThrow());
        assertSame(utils, workspace.document(utilsId).orElseThrow());
    }

    @Test
    public void workspaceRevisionTracksSuccessfulChangesAndIgnoresRejectedPublicationsAndReads() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(7), "external change");

        assertEquals(0L, store.capture().revision());

        store.publishBacking(backing);
        assertEquals(1L, store.capture().revision());

        store.publishOverlay(overlay);
        assertEquals(2L, store.capture().revision());

        store.publishBacking(newerBacking);
        WorkspaceSnapshot workspace = store.capture();
        assertEquals(3L, workspace.revision());
        assertSame(overlay, workspace.document(id).orElseThrow());
        DocumentState originalState = store.state(id).orElseThrow();
        assertTrue(originalState.isStale());

        assertThrows(IllegalArgumentException.class, () -> store.publishBacking(backing));
        assertEquals(3L, store.capture().revision());
        assertThrows(IllegalArgumentException.class,
            () -> store.publishOverlay(snapshot(new DocumentVersion(3), "older overlay")));
        assertEquals(3L, store.capture().revision());
        assertEquals(originalState, store.state(id).orElseThrow());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(3L, store.capture().revision());
    }

    @Test
    public void republishingExactSnapshotsPreservesRevisionAndStaleOverlayBase() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(7), "external change");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(newerBacking);
        DocumentState originalState = store.state(id).orElseThrow();
        assertEquals(3L, store.capture().revision());

        store.publishBacking(newerBacking);
        assertEquals(3L, store.capture().revision());
        store.publishOverlay(overlay);
        assertEquals(3L, store.capture().revision());
        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(backing.version(), store.state(id).orElseThrow().baseBackingVersion());
        assertTrue(store.state(id).orElseThrow().isStale());
        assertSame(overlay, store.current(id).orElseThrow());
    }

    @Test
    public void initialOverlayOlderThanBackingIsRejectedWithoutChangingStateOrRevision() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(3), "older overlay");
        store.publishBacking(backing);
        DocumentState originalState = store.state(id).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> store.publishOverlay(overlay));

        assertEquals(originalState, store.state(id).orElseThrow());
        assertSame(backing, store.current(id).orElseThrow());
        assertEquals(1L, store.capture().revision());
    }

    @Test
    public void equalVersionInitialOverlayWithDifferentContentIsRejectedWithoutChangingStateOrRevision() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(5), "int x = 20;");
        store.publishBacking(backing);
        DocumentState originalState = store.state(id).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> store.publishOverlay(overlay));

        assertEquals(originalState, store.state(id).orElseThrow());
        assertSame(backing, store.current(id).orElseThrow());
        assertEquals(1L, store.capture().revision());
    }

    @Test
    public void equalVersionInitialOverlayWithIdenticalContentIsAccepted() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(5), backing.text());
        store.publishBacking(backing);

        store.publishOverlay(overlay);

        DocumentState state = store.state(id).orElseThrow();
        assertSame(backing, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertFalse(state.isStale());
        assertEquals(2L, store.capture().revision());
    }

    @Test
    public void initialOverlayWithoutBackingIsAccepted() {
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(1), "unsaved document");

        store.publishOverlay(overlay);

        DocumentState state = store.state(id).orElseThrow();
        assertNull(state.backing());
        assertNull(state.baseBackingVersion());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertFalse(state.isStale());
        assertEquals(1L, store.capture().revision());
    }

    @Test
    public void staleOverlayCanBeEditedWhileItsVersionRemainsBelowBacking() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(9), "external change");
        TextDocumentSnapshot editedOverlay = snapshot(new DocumentVersion(7), "more unsaved edits");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(newerBacking);

        store.publishOverlay(editedOverlay);

        DocumentState state = store.state(id).orElseThrow();
        assertSame(newerBacking, state.backing());
        assertSame(editedOverlay, state.overlay());
        assertSame(editedOverlay, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
        assertEquals(4L, store.capture().revision());
    }

    @Test
    public void equalVersionInitialBinaryOverlayRequiresIdenticalBytes() {
        var version = new DocumentVersion(5);
        var backing = new BinaryDocumentSnapshot(id, DocumentUri.inMemory(id), version, "binary", new byte[]{1, 2, 3});

        var different = new BinaryDocumentSnapshot(id, backing.uri(), version, "binary", new byte[]{1, 2, 4});

        var identical = new BinaryDocumentSnapshot(id, backing.uri(), version, "binary", new byte[]{1, 2, 3});

        store.publishBacking(backing);
        DocumentState originalState = store.state(id).orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> store.publishOverlay(different));
        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(1L, store.capture().revision());

        store.publishOverlay(identical);

        assertSame(identical, store.current(id).orElseThrow());
        assertEquals(version, store.state(id).orElseThrow().baseBackingVersion());
        assertFalse(store.state(id).orElseThrow().isStale());
        assertEquals(2L, store.capture().revision());
    }

    @Test
    public void equalVersionInitialOverlayCannotChangeBetweenTextAndBinary() {
        TextDocumentSnapshot text = snapshot(new DocumentVersion(5), "abc");
        var binary = new BinaryDocumentSnapshot(id, text.uri(), text.version(), text.languageId(),
            text.text().getBytes(StandardCharsets.UTF_8));
        var binaryStore = new DefaultDocumentStore("project-binary", initialConfiguration);
        store.publishBacking(text);
        binaryStore.publishBacking(binary);

        assertThrows(IllegalArgumentException.class, () -> store.publishOverlay(binary));
        assertThrows(IllegalArgumentException.class, () -> binaryStore.publishOverlay(text));

        assertSame(text, store.current(id).orElseThrow());
        assertNull(store.state(id).orElseThrow().overlay());
        assertEquals(1L, store.capture().revision());
        assertSame(binary, binaryStore.current(id).orElseThrow());
        assertNull(binaryStore.state(id).orElseThrow().overlay());
        assertEquals(1L, binaryStore.capture().revision());
    }

    @Test
    public void capturedWorkspaceRetainsProjectIdAfterLaterPublications() {
        assertEquals("project-a", store.capture().projectId());
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        store.publishBacking(backing);
        WorkspaceSnapshot workspace = store.capture();

        store.publishOverlay(overlay);
        WorkspaceSnapshot newerWorkspace = store.capture();

        assertEquals("project-a", workspace.projectId());
        assertEquals(1L, workspace.revision());
        assertSame(backing, workspace.document(id).orElseThrow());
        assertEquals("project-a", newerWorkspace.projectId());
        assertEquals(2L, newerWorkspace.revision());
        assertSame(overlay, newerWorkspace.document(id).orElseThrow());
    }

    @Test
    public void storesForDifferentProjectsAdvanceWorkspaceRevisionsIndependently() {
        var otherStore = new DefaultDocumentStore("project-b", initialConfiguration);
        var otherId = DocumentId.create();
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "project A backing");
        TextDocumentSnapshot otherBacking = snapshot(otherId, new DocumentVersion(5), "project B backing");
        assertEquals(0L, store.capture().revision());
        assertEquals(0L, otherStore.capture().revision());

        store.publishBacking(backing);
        assertEquals(1L, store.capture().revision());
        assertEquals(0L, otherStore.capture().revision());

        otherStore.publishBacking(otherBacking);
        assertEquals(1L, store.capture().revision());
        assertEquals(1L, otherStore.capture().revision());

        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "project A edits");
        store.publishOverlay(overlay);
        assertEquals(2L, store.capture().revision());
        assertEquals(1L, otherStore.capture().revision());

        TextDocumentSnapshot newerOtherBacking = snapshot(otherId, new DocumentVersion(6), "project B external change");
        otherStore.publishBacking(newerOtherBacking);
        assertEquals(2L, store.capture().revision());
        assertEquals(2L, otherStore.capture().revision());

        TextDocumentSnapshot otherOverlay = snapshot(otherId, new DocumentVersion(7), "project B edits");
        otherStore.publishOverlay(otherOverlay);
        WorkspaceSnapshot workspace = store.capture();
        WorkspaceSnapshot otherWorkspace = otherStore.capture();
        assertEquals("project-a", workspace.projectId());
        assertEquals("project-b", otherWorkspace.projectId());
        assertEquals(2L, workspace.revision());
        assertEquals(3L, otherWorkspace.revision());
        assertSame(overlay, workspace.document(id).orElseThrow());
        assertSame(otherOverlay, otherWorkspace.document(otherId).orElseThrow());
        assertEquals(Optional.empty(), workspace.document(otherId));
        assertEquals(Optional.empty(), otherWorkspace.document(id));
    }

    @Test
    public void captureStartsWithSuppliedEmptyConfigurationAtRevisionZero() {
        WorkspaceSnapshot workspace = store.capture();

        assertEquals("project-a", workspace.projectId());
        assertEquals(0L, workspace.revision());
        assertTrue(workspace.documents().isEmpty());
        assertSame(initialConfiguration, workspace.configuration());
        assertEquals(0L, workspace.configuration().revision());
        assertEquals(Set.of(), workspace.configuration().moduleIds());
    }

    @Test
    public void captureRetainsPublishedConfigurationAndDocumentsAfterLaterChanges() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "edits");
        var configuration = new WorkspaceConfigurationSnapshot(7, Set.of("main"));
        var newerConfiguration = new WorkspaceConfigurationSnapshot(8, Set.of("main", "tests"));
        store.publishBacking(backing);
        store.publishConfiguration(configuration);
        WorkspaceSnapshot workspace = store.capture();

        store.publishOverlay(overlay);
        store.publishConfiguration(newerConfiguration);
        WorkspaceSnapshot newerWorkspace = store.capture();

        assertEquals("project-a", workspace.projectId());
        assertEquals(2L, workspace.revision());
        assertSame(configuration, workspace.configuration());
        assertSame(backing, workspace.document(id).orElseThrow());
        assertEquals("project-a", newerWorkspace.projectId());
        assertEquals(4L, newerWorkspace.revision());
        assertSame(newerConfiguration, newerWorkspace.configuration());
        assertSame(overlay, newerWorkspace.document(id).orElseThrow());
    }

    @Test
    public void configurationRevisionAdvancesIndependentlyOfWorkspaceRevision() {
        store.publishBacking(snapshot(new DocumentVersion(5), "backing"));
        store.publishOverlay(snapshot(new DocumentVersion(6), "edits"));
        assertEquals(2L, store.capture().revision());

        var configuration = new WorkspaceConfigurationSnapshot(1, Set.of("main"));
        store.publishConfiguration(configuration);
        assertEquals(3L, store.capture().revision());
        assertSame(configuration, store.capture().configuration());

        var newerConfiguration = new WorkspaceConfigurationSnapshot(10, Set.of("main", "tests"));
        store.publishConfiguration(newerConfiguration);
        assertEquals(4L, store.capture().revision());
        assertSame(newerConfiguration, store.capture().configuration());
    }

    @Test
    public void rejectedOrUnchangedConfigurationsPreserveCapturedStateAndRevision() {
        var configuration = new WorkspaceConfigurationSnapshot(7, Set.of("main"));
        store.publishBacking(snapshot(new DocumentVersion(5), "backing"));
        store.publishConfiguration(configuration);
        WorkspaceSnapshot originalWorkspace = store.capture();

        assertThrows(IllegalArgumentException.class,
            () -> store.publishConfiguration(new WorkspaceConfigurationSnapshot(6, Set.of("main"))));
        assertEquals(originalWorkspace, store.capture());

        assertThrows(IllegalArgumentException.class,
            () -> store.publishConfiguration(new WorkspaceConfigurationSnapshot(7, Set.of("changed"))));
        assertEquals(originalWorkspace, store.capture());

        store.publishConfiguration(new WorkspaceConfigurationSnapshot(7, Set.of("main")));
        assertEquals(originalWorkspace, store.capture());
        assertSame(configuration, store.capture().configuration());
    }

    @Test
    public void configurationOnlyChangeAdvancesWorkspaceRevisionAndPreservesEarlierCapture() {
        var configuration = new WorkspaceConfigurationSnapshot(1, Set.of("core", "java"));
        store.publishConfiguration(configuration);
        WorkspaceSnapshot workspace = store.capture();
        assertEquals(1L, workspace.revision());

        var newerConfiguration = new WorkspaceConfigurationSnapshot(2, Set.of("core", "java", "resources"));
        store.publishConfiguration(newerConfiguration);
        WorkspaceSnapshot newerWorkspace = store.capture();

        assertSame(newerConfiguration, newerWorkspace.configuration());
        assertEquals(2L, newerWorkspace.configuration().revision());
        assertEquals(Set.of("core", "java", "resources"), newerWorkspace.configuration().moduleIds());
        assertEquals(workspace.revision() + 1, newerWorkspace.revision());
        assertEquals(2L, newerWorkspace.revision());
        assertTrue(newerWorkspace.documents().isEmpty());

        assertSame(configuration, workspace.configuration());
        assertEquals(1L, workspace.configuration().revision());
        assertEquals(Set.of("core", "java"), workspace.configuration().moduleIds());
        assertEquals(1L, workspace.revision());
        assertTrue(workspace.documents().isEmpty());
    }

    @Test
    public void firstCaptureRetainsSuppliedInitialConfigurationAtWorkspaceRevisionZero() {
        var configuration = new WorkspaceConfigurationSnapshot(7, Set.of("core", "java"));
        var initializedStore = new DefaultDocumentStore("existing-project", configuration);

        WorkspaceSnapshot workspace = initializedStore.capture();

        assertEquals("existing-project", workspace.projectId());
        assertEquals(0L, workspace.revision());
        assertSame(configuration, workspace.configuration());
        assertEquals(7L, workspace.configuration().revision());
        assertEquals(Set.of("core", "java"), workspace.configuration().moduleIds());
        assertTrue(workspace.documents().isEmpty());
    }

    @Test
    public void sameVersionBackingWithDifferentTextIsRejectedWithoutChangingStateOrRevision() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        TextDocumentSnapshot replacement = snapshot(new DocumentVersion(5), "int x = 20;");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        assertThrows(IllegalArgumentException.class, () -> store.publishBacking(replacement));

        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());
        assertEquals(2L, store.capture().revision());
        assertSame(backing, store.state(id).orElseThrow().backing());
        assertSame(overlay, store.current(id).orElseThrow());
    }

    @Test
    public void sameVersionOverlayWithDifferentTextIsRejectedWithoutChangingStateOrRevision() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(4), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(7), "external change");
        TextDocumentSnapshot replacement = snapshot(new DocumentVersion(5), "int x = 20;");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(newerBacking);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        assertThrows(IllegalArgumentException.class, () -> store.publishOverlay(replacement));

        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());
        assertEquals(3L, store.capture().revision());
        assertSame(newerBacking, store.state(id).orElseThrow().backing());
        assertSame(overlay, store.current(id).orElseThrow());
    }

    @Test
    public void sameVersionBackingWithIdenticalContentAllowsMetadataChanges() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        var replacement = new TextDocumentSnapshot(
            id, DocumentUri.virtual("generated", "/Renamed.txt"), backing.version(), "text",
            new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.GENERATED),
            backing.text(), StandardCharsets.UTF_16);
        store.publishBacking(backing);

        store.publishBacking(replacement);

        assertSame(replacement, store.state(id).orElseThrow().backing());
        assertSame(replacement, store.current(id).orElseThrow());
        assertEquals(backing.version(), store.current(id).orElseThrow().version());
        assertEquals(2L, store.capture().revision());
    }

    @Test
    public void sameVersionStaleOverlayWithIdenticalContentAllowsMetadataChangesAndPreservesBase() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(4), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(7), "external change");
        var replacement = new TextDocumentSnapshot(
            id, DocumentUri.virtual("generated", "/Renamed.txt"), overlay.version(), "text",
            new DocumentOrigin(BackingKind.VIRTUAL, ProvenanceKind.GENERATED),
            overlay.text(), StandardCharsets.UTF_16);
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(newerBacking);

        store.publishOverlay(replacement);

        DocumentState state = store.state(id).orElseThrow();
        assertSame(newerBacking, state.backing());
        assertSame(replacement, state.overlay());
        assertSame(replacement, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
        assertEquals(4L, store.capture().revision());
    }

    @Test
    public void sameVersionBinaryPublicationsRejectChangedBytesAndAllowIdenticalBytes() {
        var backing = new BinaryDocumentSnapshot(id, DocumentUri.inMemory(id), new DocumentVersion(5),
            "binary", new byte[]{1, 2});

        var overlay = new BinaryDocumentSnapshot(id, backing.uri(), new DocumentVersion(6),
            "binary", new byte[]{3, 4});

        var changedBacking = new BinaryDocumentSnapshot(id, backing.uri(), backing.version(),
            "binary", new byte[]{1, 3});

        var changedOverlay = new BinaryDocumentSnapshot(id, overlay.uri(), overlay.version(),
            "binary", new byte[]{3, 5});

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        assertThrows(IllegalArgumentException.class, () -> store.publishBacking(changedBacking));
        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());
        assertThrows(IllegalArgumentException.class, () -> store.publishOverlay(changedOverlay));
        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());

        var identicalBacking = new BinaryDocumentSnapshot(id, backing.uri(), backing.version(),
            "binary", new byte[]{1, 2});

        var identicalOverlay = new BinaryDocumentSnapshot(id, overlay.uri(), overlay.version(),
            "binary", new byte[]{3, 4});

        store.publishBacking(identicalBacking);
        store.publishOverlay(identicalOverlay);

        DocumentState state = store.state(id).orElseThrow();
        assertSame(identicalBacking, state.backing());
        assertSame(identicalOverlay, state.overlay());
        assertSame(identicalOverlay, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertEquals(4L, store.capture().revision());
    }

    @Test
    public void removingBackingOnlyDocumentRemovesItFromCurrentStateAndCapture() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        store.publishBacking(backing);
        WorkspaceSnapshot originalWorkspace = store.capture();

        store.removeBacking(id, backing.version());

        assertEquals(Optional.empty(), store.current(id));
        assertEquals(Optional.empty(), store.state(id));
        WorkspaceSnapshot workspace = store.capture();
        assertEquals(Optional.empty(), workspace.document(id));
        assertTrue(workspace.documents().isEmpty());
        assertEquals(2L, workspace.revision());
        assertSame(backing, originalWorkspace.document(id).orElseThrow());
        assertEquals(1L, originalWorkspace.revision());
    }

    @Test
    public void removingBackingPreservesExactOverlayAndBaseAndMakesItStale() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();
        assertFalse(originalState.isStale());

        store.removeBacking(id, backing.version());

        DocumentState state = store.state(id).orElseThrow();
        assertNull(state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
        WorkspaceSnapshot workspace = store.capture();
        assertEquals(1, workspace.documents().size());
        assertSame(overlay, workspace.document(id).orElseThrow());
        assertEquals(3L, workspace.revision());
        assertSame(backing, originalState.backing());
        assertFalse(originalState.isStale());
    }

    @Test
    public void removingBackingWithWrongExpectedVersionPreservesStateAndWorkspaceRevision() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "backing");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        for (DocumentVersion expectedVersion : List.of(new DocumentVersion(3), new DocumentVersion(7))) {
            assertThrows(IllegalStateException.class, () -> store.removeBacking(id, expectedVersion));
            assertEquals(originalState, store.state(id).orElseThrow());
            assertEquals(originalWorkspace, store.capture());
            assertSame(backing, store.state(id).orElseThrow().backing());
            assertSame(overlay, store.current(id).orElseThrow());
        }
    }

    @Test
    public void removingAbsentBackingPreservesStateAndWorkspaceRevision() {
        WorkspaceSnapshot emptyWorkspace = store.capture();
        assertThrows(IllegalStateException.class, () -> store.removeBacking(id, new DocumentVersion(5)));
        assertEquals(emptyWorkspace, store.capture());

        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved document");
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        assertThrows(IllegalStateException.class, () -> store.removeBacking(id, new DocumentVersion(5)));

        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());
        assertSame(overlay, store.current(id).orElseThrow());
        assertFalse(store.state(id).orElseThrow().isStale());
    }

    @Test
    public void restoringBackingPreservesStaleOverlay() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "int x = 20;");
        TextDocumentSnapshot restoredBacking = snapshot(new DocumentVersion(7), "int x = 30;");
        store.publishBacking(backing);
        store.publishOverlay(overlay);

        store.removeBacking(id, backing.version());
        DocumentState deletedState = store.state(id).orElseThrow();
        assertNull(deletedState.backing());
        assertTrue(deletedState.isStale());

        store.publishBacking(restoredBacking);

        DocumentState state = store.state(id).orElseThrow();
        assertSame(restoredBacking, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(new DocumentVersion(5), state.baseBackingVersion());
        assertTrue(state.isStale());
        WorkspaceSnapshot workspace = store.capture();
        assertEquals(1, workspace.documents().size());
        assertSame(overlay, workspace.document(id).orElseThrow());
        assertEquals(4L, workspace.revision());
    }

    @Test
    public void deletedBackingRejectsReusedOrOlderVersionsWithoutChangingWorkspace() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original content");
        store.publishBacking(backing);
        store.removeBacking(id, backing.version());
        WorkspaceSnapshot originalWorkspace = store.capture();

        List<TextDocumentSnapshot> rejectedSnapshots = List.of(
            snapshot(new DocumentVersion(5), "different content"),
            snapshot(new DocumentVersion(4), "older content"),
            backing);
        for (TextDocumentSnapshot rejected : rejectedSnapshots) {
            assertThrows(IllegalArgumentException.class, () -> store.publishBacking(rejected));
            assertEquals(Optional.empty(), store.current(id));
            assertEquals(Optional.empty(), store.state(id));
            assertEquals(originalWorkspace, store.capture());
            assertEquals(2L, store.capture().revision());
        }
    }

    @Test
    public void deletedBackingAcceptsStrictlyNewerVersion() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original content");
        TextDocumentSnapshot restoredBacking = snapshot(new DocumentVersion(6), "recreated content");
        store.publishBacking(backing);
        store.removeBacking(id, backing.version());

        store.publishBacking(restoredBacking);

        DocumentState state = store.state(id).orElseThrow();
        assertSame(restoredBacking, state.backing());
        assertNull(state.overlay());
        assertSame(restoredBacking, store.current(id).orElseThrow());
        WorkspaceSnapshot workspace = store.capture();
        assertSame(restoredBacking, workspace.document(id).orElseThrow());
        assertEquals(3L, workspace.revision());
    }

    @Test
    public void deletedBackingHistoryTracksLatestBackingRatherThanOverlayBase() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original content");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(7), "external change");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(newerBacking);
        store.removeBacking(id, newerBacking.version());
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        for (DocumentVersion version : List.of(new DocumentVersion(5), new DocumentVersion(6),
            new DocumentVersion(7))) {
            TextDocumentSnapshot rejected = snapshot(version, "reused backing revision");
            assertThrows(IllegalArgumentException.class, () -> store.publishBacking(rejected));
            assertEquals(originalState, store.state(id).orElseThrow());
            assertEquals(originalWorkspace, store.capture());
            assertSame(overlay, store.current(id).orElseThrow());
            assertEquals(4L, store.capture().revision());
        }

        TextDocumentSnapshot restoredBacking = snapshot(new DocumentVersion(8), "recreated content");
        store.publishBacking(restoredBacking);

        DocumentState state = store.state(id).orElseThrow();
        assertSame(restoredBacking, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
        assertEquals(5L, store.capture().revision());
    }

    @Test
    public void deletedBackingRemembersCommittedOverlayVersion() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original content");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "saved edits");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.commitOverlay(id, backing.version(), overlay.version());
        store.removeBacking(id, overlay.version());
        WorkspaceSnapshot originalWorkspace = store.capture();

        assertThrows(IllegalArgumentException.class,
            () -> store.publishBacking(snapshot(overlay.version(), "reused committed revision")));

        assertEquals(Optional.empty(), store.current(id));
        assertEquals(originalWorkspace, store.capture());
        assertEquals(4L, store.capture().revision());
    }

    @Test
    public void deletedBackingRemembersDiscardedRevision() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original content");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        TextDocumentSnapshot restoredBacking = snapshot(new DocumentVersion(7), backing.text());
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.discardOverlay(id, backing.version(), overlay.version(), restoredBacking);
        store.removeBacking(id, restoredBacking.version());
        WorkspaceSnapshot originalWorkspace = store.capture();

        for (DocumentVersion version : List.of(new DocumentVersion(6), new DocumentVersion(7))) {
            TextDocumentSnapshot rejected = snapshot(version, "reused discarded revision");
            assertThrows(IllegalArgumentException.class, () -> store.publishBacking(rejected));
            assertEquals(Optional.empty(), store.current(id));
            assertEquals(originalWorkspace, store.capture());
            assertEquals(4L, store.capture().revision());
        }
    }

    @Test
    public void removingUnchangedOverlayRetainsExactBackingAndVersion() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(5), backing.text());
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        WorkspaceSnapshot originalWorkspace = store.capture();
        assertSame(overlay, store.current(id).orElseThrow());

        store.removeUnchangedOverlay(id, backing.version(), overlay.version());

        DocumentState state = store.state(id).orElseThrow();
        assertSame(backing, state.backing());
        assertSame(backing, store.current(id).orElseThrow());
        assertEquals(new DocumentVersion(5), store.current(id).orElseThrow().version());
        assertNull(state.overlay());
        assertNull(state.baseBackingVersion());
        assertFalse(state.isStale());
        WorkspaceSnapshot workspace = store.capture();
        assertSame(backing, workspace.document(id).orElseThrow());
        assertEquals(3L, workspace.revision());
        assertSame(overlay, originalWorkspace.document(id).orElseThrow());
        assertEquals(2L, originalWorkspace.revision());
    }

    @Test
    public void removingModifiedOverlayAsUnchangedIsRejectedWithoutChangingStateOrRevision() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "int x = 20;");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        assertThrows(IllegalStateException.class,
            () -> store.removeUnchangedOverlay(id, backing.version(), overlay.version()));

        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(2L, store.capture().revision());
    }

    @Test
    public void removingStaleOverlayAsUnchangedIsRejectedWithoutChangingStateOrRevision() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "int x = 20;");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(7), "int x = 30;");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(newerBacking);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> store.removeUnchangedOverlay(id, newerBacking.version(), overlay.version()));

        assertEquals("Cannot remove a stale overlay as unchanged.", exception.getMessage());
        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(3L, store.capture().revision());
    }

    @Test
    public void removingUnchangedOverlayWithMismatchedExpectedVersionsPreservesStateAndRevision() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(5), backing.text());
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        assertThrows(IllegalStateException.class,
            () -> store.removeUnchangedOverlay(id, new DocumentVersion(4), overlay.version()));
        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());

        assertThrows(IllegalStateException.class,
            () -> store.removeUnchangedOverlay(id, backing.version(), new DocumentVersion(4)));
        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(2L, store.capture().revision());
    }

    @Test
    public void removingUnchangedOverlayRequiresBothBackingAndOverlay() {
        WorkspaceSnapshot emptyWorkspace = store.capture();
        assertThrows(IllegalStateException.class,
            () -> store.removeUnchangedOverlay(id, new DocumentVersion(5), new DocumentVersion(5)));
        assertEquals(emptyWorkspace, store.capture());

        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "int x = 10;");
        store.publishBacking(backing);
        WorkspaceSnapshot backingOnlyWorkspace = store.capture();
        assertThrows(IllegalStateException.class,
            () -> store.removeUnchangedOverlay(id, backing.version(), backing.version()));
        assertEquals(backingOnlyWorkspace, store.capture());

        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(5), backing.text());
        store.publishOverlay(overlay);
        store.removeBacking(id, backing.version());
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot overlayOnlyWorkspace = store.capture();
        assertThrows(IllegalStateException.class,
            () -> store.removeUnchangedOverlay(id, backing.version(), overlay.version()));
        assertEquals(originalState, store.state(id).orElseThrow());
        assertEquals(overlayOnlyWorkspace, store.capture());
        assertSame(overlay, store.current(id).orElseThrow());
    }

    @Test
    public void backingPublicationCannotConflictWithOverlayAtSameRevision() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved changes");
        TextDocumentSnapshot conflictingBacking = snapshot(new DocumentVersion(6), "external changes");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> store.publishBacking(conflictingBacking));

        assertEquals("Backing and overlay cannot have different content at the same document version.",
            exception.getMessage());
        DocumentState state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(backing, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());
        assertEquals(2L, store.capture().revision());
    }

    @Test
    public void overlayPublicationCannotConflictWithBackingAtSameRevision() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved changes");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(7), "external changes");
        TextDocumentSnapshot conflictingOverlay = snapshot(new DocumentVersion(7), "further unsaved changes");
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(newerBacking);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> store.publishOverlay(conflictingOverlay));

        assertEquals("Backing and overlay cannot have different content at the same document version.",
            exception.getMessage());
        DocumentState state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(newerBacking, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());
        assertEquals(3L, store.capture().revision());
    }

    @Test
    public void backingCanShareOverlayRevisionWhenContentMatchesWithoutReconcilingOverlay() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved changes");
        TextDocumentSnapshot matchingBacking = snapshot(overlay.version(), overlay.text());
        store.publishBacking(backing);
        store.publishOverlay(overlay);

        store.publishBacking(matchingBacking);

        DocumentState state = store.state(id).orElseThrow();
        assertSame(matchingBacking, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
        WorkspaceSnapshot workspace = store.capture();
        assertSame(overlay, workspace.document(id).orElseThrow());
        assertEquals(3L, workspace.revision());
    }

    @Test
    public void existingOverlayCanShareBackingRevisionWhenContentMatchesWithoutChangingBase() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(5), "original");
        TextDocumentSnapshot overlay = snapshot(new DocumentVersion(6), "unsaved changes");
        TextDocumentSnapshot newerBacking = snapshot(new DocumentVersion(7), "external changes");
        TextDocumentSnapshot matchingOverlay = snapshot(newerBacking.version(), newerBacking.text());
        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(newerBacking);

        store.publishOverlay(matchingOverlay);

        DocumentState state = store.state(id).orElseThrow();
        assertSame(newerBacking, state.backing());
        assertSame(matchingOverlay, state.overlay());
        assertSame(matchingOverlay, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
        WorkspaceSnapshot workspace = store.capture();
        assertSame(matchingOverlay, workspace.document(id).orElseThrow());
        assertEquals(4L, workspace.revision());
    }

    @Test
    public void initialOverlayCannotConflictWithBackingAtSameRevision() {
        TextDocumentSnapshot backing = snapshot(new DocumentVersion(6), "external changes");
        TextDocumentSnapshot conflictingOverlay = snapshot(new DocumentVersion(6), "unsaved changes");
        store.publishBacking(backing);
        DocumentState originalState = store.state(id).orElseThrow();
        WorkspaceSnapshot originalWorkspace = store.capture();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> store.publishOverlay(conflictingOverlay));

        assertEquals("Backing and overlay cannot have different content at the same document version.",
            exception.getMessage());
        DocumentState state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(backing, state.backing());
        assertNull(state.overlay());
        assertSame(backing, store.current(id).orElseThrow());
        assertEquals(originalWorkspace, store.capture());
        assertEquals(1L, store.capture().revision());
    }

    @Test
    public void concurrentPublicationAndCaptureRemainConsistent() throws Exception {
        List<TextDocumentSnapshot> publications = new ArrayList<TextDocumentSnapshot>();
        for (int version = 1; version <= 100; version++) {
            publications.add(snapshot(new DocumentVersion(version), "revision " + version));
        }

        WorkspaceSnapshot initialWorkspace = store.capture();
        assertCaptureMatchesPublishedBacking(initialWorkspace, publications);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var midpointCaptured = new CountDownLatch(1);
        var writerDone = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> writer = executor.submit(() -> {
                ready.countDown();
                try {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    for (TextDocumentSnapshot publication : publications) {
                        store.publishBacking(publication);
                        if (publication.version().value() == 50) {
                            assertTrue(midpointCaptured.await(10, TimeUnit.SECONDS));
                        }
                    }

                    return null;
                } finally {
                    writerDone.countDown();
                }
            });

            Future<List<WorkspaceSnapshot>> reader = executor.submit(() -> {
                ready.countDown();
                try {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    ArrayList<WorkspaceSnapshot> captures = new ArrayList<WorkspaceSnapshot>();
                    long previousRevision = 0;
                    do {
                        WorkspaceSnapshot workspace = store.capture();
                        assertCaptureMatchesPublishedBacking(workspace, publications);
                        assertTrue(workspace.revision() >= previousRevision, "Captured revisions must not decrease");
                        previousRevision = workspace.revision();
                        captures.add(workspace);
                        if (workspace.revision() == 50) {
                            midpointCaptured.countDown();
                        }
                    } while (writerDone.getCount() != 0);

                    WorkspaceSnapshot finalWorkspace = store.capture();
                    assertCaptureMatchesPublishedBacking(finalWorkspace, publications);
                    assertTrue(finalWorkspace.revision() >= previousRevision);
                    assertEquals(100L, finalWorkspace.revision());
                    captures.add(finalWorkspace);
                    return captures;
                } finally {
                    midpointCaptured.countDown();
                }
            });

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            writer.get(20, TimeUnit.SECONDS);
            List<WorkspaceSnapshot> captures = reader.get(20, TimeUnit.SECONDS);
            assertTrue(captures.stream().anyMatch(workspace -> workspace.revision() == 50));

            assertCaptureMatchesPublishedBacking(initialWorkspace, publications);
            for (WorkspaceSnapshot workspace : captures) {
                assertCaptureMatchesPublishedBacking(workspace, publications);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    public void blankProjectIdsAreRejectedByStoreConstructor() {
        for (String projectId : List.of("", " ", "\t\n")) {
            assertThrows(IllegalArgumentException.class,
                () -> new DefaultDocumentStore(projectId, initialConfiguration));
        }
    }

    private void assertCaptureMatchesPublishedBacking(
        WorkspaceSnapshot workspace,
        List<TextDocumentSnapshot> publications
    ) {
        assertEquals("project-a", workspace.projectId());
        assertSame(initialConfiguration, workspace.configuration());
        long revision = workspace.revision();
        if (revision == 0) {
            assertTrue(workspace.documents().isEmpty());
            assertEquals(Optional.empty(), workspace.document(id));
        } else {
            assertTrue(revision > 0 && revision <= publications.size());
            assertEquals(1, workspace.documents().size());
            DocumentSnapshot document = workspace.document(id).orElseThrow();
            assertEquals(revision, document.version().value());
            assertSame(publications.get(Math.toIntExact(revision) - 1), document);
            assertEquals("revision " + revision, ((TextDocumentSnapshot) document).text());
        }
    }

    private TextDocumentSnapshot snapshot(DocumentVersion version, String text) {
        return snapshot(id, version, text);
    }

    private TextDocumentSnapshot snapshot(DocumentId documentId, DocumentVersion version, String text) {
        return new TextDocumentSnapshot(
            documentId,
            DocumentUri.inMemory(documentId),
            version,
            "java",
            text,
            StandardCharsets.UTF_8);
    }
}
