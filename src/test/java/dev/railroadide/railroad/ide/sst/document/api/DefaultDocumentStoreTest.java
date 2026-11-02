package dev.railroadide.railroad.ide.sst.document.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultDocumentStoreTest {
    private final DefaultDocumentStore store = new DefaultDocumentStore();
    private final DocumentId id = DocumentId.create();

    @Test
    public void unknownDocumentReturnsEmpty() {
        assertEquals(Optional.empty(), store.current(id));
        assertEquals(Optional.empty(), store.state(id));
    }

    @Test
    public void backingOnlyReturnsBacking() {
        var backing = snapshot(DocumentVersion.initial(), "backing");

        store.publishBacking(backing);

        assertSame(backing, store.current(id).orElseThrow());
        var state = store.state(id).orElseThrow();
        assertSame(backing, state.backing());
        assertNull(state.overlay());
        assertNull(state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void overlayOnlyReturnsOverlay() {
        var overlay = snapshot(DocumentVersion.initial(), "overlay");

        store.publishOverlay(overlay);

        assertSame(overlay, store.current(id).orElseThrow());
        var state = store.state(id).orElseThrow();
        assertNull(state.backing());
        assertSame(overlay, state.overlay());
        assertNull(state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void backingAndOverlayReturnsOverlay() {
        var backing = snapshot(DocumentVersion.initial(), "backing");
        var overlay = snapshot(backing.version().next(), "overlay");

        store.publishBacking(backing);
        store.publishOverlay(overlay);

        assertSame(overlay, store.current(id).orElseThrow());
    }

    @Test
    public void replacingBackingWhileOverlayExistsStillReturnsOverlay() {
        var backing = snapshot(DocumentVersion.initial(), "backing");
        var overlay = snapshot(backing.version().next(), "overlay");
        var replacementBacking = snapshot(overlay.version().next(), "replacement backing");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(replacementBacking);

        assertSame(overlay, store.current(id).orElseThrow());
    }

    @Test
    public void replacingOverlayReturnsNewOverlay() {
        var backing = snapshot(DocumentVersion.initial(), "backing");
        var overlay = snapshot(backing.version().next(), "overlay");
        var replacementOverlay = snapshot(overlay.version().next(), "replacement overlay");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishOverlay(replacementOverlay);

        assertSame(replacementOverlay, store.current(id).orElseThrow());
    }

    @Test
    public void olderBackingIsRejectedAndOriginalSnapshotRemainsCurrent() {
        var backing = snapshot(new DocumentVersion(5), "backing");
        var olderBacking = snapshot(new DocumentVersion(3), "older backing");

        store.publishBacking(backing);

        assertThrows(IllegalArgumentException.class, () -> store.publishBacking(olderBacking));
        assertSame(backing, store.current(id).orElseThrow());
    }

    @Test
    public void olderOverlayIsRejectedAndOriginalSnapshotRemainsCurrent() {
        var overlay = snapshot(new DocumentVersion(5), "overlay");
        var olderOverlay = snapshot(new DocumentVersion(3), "older overlay");

        store.publishOverlay(overlay);

        assertThrows(IllegalArgumentException.class, () -> store.publishOverlay(olderOverlay));
        assertSame(overlay, store.current(id).orElseThrow());
    }

    @Test
    public void rejectedPublicationsPreserveOverlayAndBackingVersionGuard() {
        var backing = snapshot(new DocumentVersion(5), "backing");
        var overlay = snapshot(new DocumentVersion(6), "overlay");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        var originalState = store.state(id).orElseThrow();

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
        var backing = snapshot(new DocumentVersion(5), "backing");
        var newerBacking = snapshot(new DocumentVersion(7), "newer backing");

        store.publishBacking(backing);
        store.publishBacking(newerBacking);

        assertSame(newerBacking, store.current(id).orElseThrow());
    }

    @Test
    public void newerOverlayReplacesCurrentOverlay() {
        var overlay = snapshot(new DocumentVersion(5), "overlay");
        var newerOverlay = snapshot(new DocumentVersion(7), "newer overlay");

        store.publishOverlay(overlay);
        store.publishOverlay(newerOverlay);

        assertSame(newerOverlay, store.current(id).orElseThrow());
    }

    @Test
    public void sameBackingVersionCanBeRepublished() {
        var backing = snapshot(new DocumentVersion(5), "backing");
        var replacement = snapshot(new DocumentVersion(5), "backing");

        store.publishBacking(backing);
        store.publishBacking(backing);
        assertSame(backing, store.current(id).orElseThrow());

        store.publishBacking(replacement);
        assertSame(replacement, store.current(id).orElseThrow());
    }

    @Test
    public void sameOverlayVersionCanBeRepublished() {
        var overlay = snapshot(new DocumentVersion(5), "overlay");
        var replacement = snapshot(new DocumentVersion(5), "overlay");

        store.publishOverlay(overlay);
        store.publishOverlay(overlay);
        assertSame(overlay, store.current(id).orElseThrow());

        store.publishOverlay(replacement);
        assertSame(replacement, store.current(id).orElseThrow());
    }

    @Test
    public void stateCapturesOverlayBaseAndDetectsNewerBacking() {
        var backing = snapshot(new DocumentVersion(5), "backing");
        var overlay = snapshot(new DocumentVersion(6), "overlay");
        var newerBacking = snapshot(new DocumentVersion(7), "external change");

        store.publishBacking(backing);
        store.publishOverlay(overlay);

        var originalState = store.state(id).orElseThrow();
        assertSame(backing, originalState.backing());
        assertSame(overlay, originalState.overlay());
        assertEquals(backing.version(), originalState.baseBackingVersion());
        assertFalse(originalState.isStale());

        store.publishBacking(snapshot(backing.version(), "backing"));
        assertFalse(store.state(id).orElseThrow().isStale());

        store.publishBacking(newerBacking);

        var state = store.state(id).orElseThrow();
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
        var backing = snapshot(new DocumentVersion(5), "backing");
        var overlay = snapshot(new DocumentVersion(6), "overlay");
        var editedOverlay = snapshot(new DocumentVersion(7), "edited overlay");
        var newerBacking = snapshot(new DocumentVersion(8), "external change");
        var laterOverlay = snapshot(new DocumentVersion(9), "later overlay edits");

        store.publishBacking(backing);
        var backingOnly = store.state(id).orElseThrow();
        assertSame(backing, backingOnly.backing());
        assertNull(backingOnly.overlay());
        assertNull(backingOnly.baseBackingVersion());
        assertFalse(backingOnly.isStale());

        store.publishOverlay(overlay);
        var firstOverlay = store.state(id).orElseThrow();
        assertSame(backing, firstOverlay.backing());
        assertSame(overlay, firstOverlay.overlay());
        assertEquals(backing.version(), firstOverlay.baseBackingVersion());
        assertFalse(firstOverlay.isStale());
        assertSame(overlay, store.current(id).orElseThrow());

        store.publishOverlay(editedOverlay);
        var updatedOverlay = store.state(id).orElseThrow();
        assertSame(backing, updatedOverlay.backing());
        assertSame(editedOverlay, updatedOverlay.overlay());
        assertEquals(backing.version(), updatedOverlay.baseBackingVersion());
        assertFalse(updatedOverlay.isStale());
        assertSame(editedOverlay, store.current(id).orElseThrow());

        store.publishBacking(newerBacking);
        var state = store.state(id).orElseThrow();
        assertSame(newerBacking, state.backing());
        assertSame(editedOverlay, state.overlay());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
        assertSame(editedOverlay, store.current(id).orElseThrow());

        store.publishOverlay(laterOverlay);
        var laterState = store.state(id).orElseThrow();
        assertSame(newerBacking, laterState.backing());
        assertSame(laterOverlay, laterState.overlay());
        assertEquals(backing.version(), laterState.baseBackingVersion());
        assertTrue(laterState.isStale());
        assertSame(laterOverlay, store.current(id).orElseThrow());

        var republishedOverlay = snapshot(laterOverlay.version(), laterOverlay.text());
        store.publishOverlay(republishedOverlay);
        var republishedState = store.state(id).orElseThrow();
        assertSame(newerBacking, republishedState.backing());
        assertSame(republishedOverlay, republishedState.overlay());
        assertEquals(backing.version(), republishedState.baseBackingVersion());
        assertTrue(republishedState.isStale());
        assertSame(republishedOverlay, store.current(id).orElseThrow());
    }

    @Test
    public void overlayWithoutBackingRetainsUnknownBaseWhenBackingArrives() {
        var overlay = snapshot(new DocumentVersion(6), "overlay");
        var backing = snapshot(new DocumentVersion(7), "backing");
        var editedOverlay = snapshot(new DocumentVersion(8), "edited overlay");

        store.publishOverlay(overlay);
        var overlayOnly = store.state(id).orElseThrow();
        assertNull(overlayOnly.backing());
        assertSame(overlay, overlayOnly.overlay());
        assertNull(overlayOnly.baseBackingVersion());
        assertFalse(overlayOnly.isStale());
        assertSame(overlay, store.current(id).orElseThrow());

        store.publishBacking(backing);

        var state = store.state(id).orElseThrow();
        assertSame(backing, state.backing());
        assertSame(overlay, state.overlay());
        assertNull(state.baseBackingVersion());
        assertTrue(state.isStale());
        assertSame(overlay, store.current(id).orElseThrow());

        store.publishOverlay(editedOverlay);

        var editedState = store.state(id).orElseThrow();
        assertSame(editedOverlay, editedState.overlay());
        assertNull(editedState.baseBackingVersion());
        assertTrue(editedState.isStale());
        assertSame(editedOverlay, store.current(id).orElseThrow());
    }

    @Test
    public void reconciliationPublishesExactResolvedSnapshotAndUpdatesBase() {
        var originalBacking = snapshot(new DocumentVersion(5), "original backing");
        var overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        var backing = snapshot(new DocumentVersion(7), "external change");
        var resolved = snapshot(new DocumentVersion(8), "resolved edits");

        store.publishBacking(originalBacking);
        store.publishOverlay(overlay);
        store.publishBacking(backing);
        assertTrue(store.state(id).orElseThrow().isStale());

        store.reconcileOverlay(id, backing.version(), overlay.version(), resolved);

        var state = store.state(id).orElseThrow();
        assertSame(backing, state.backing());
        assertSame(resolved, state.overlay());
        assertSame(resolved, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void reconciliationRejectsBackingChangedSinceRequestWithoutChangingState() {
        var originalBacking = snapshot(new DocumentVersion(5), "original backing");
        var overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        var expectedBacking = snapshot(new DocumentVersion(7), "external change");
        var newerBacking = snapshot(new DocumentVersion(9), "another external change");
        var resolved = snapshot(new DocumentVersion(10), "resolved edits");

        store.publishBacking(originalBacking);
        store.publishOverlay(overlay);
        store.publishBacking(expectedBacking);
        store.publishBacking(newerBacking);
        var originalState = store.state(id).orElseThrow();

        var exception = assertThrows(IllegalStateException.class,
            () -> store.reconcileOverlay(id, expectedBacking.version(), overlay.version(), resolved));

        assertEquals(
            "Cannot reconcile overlay for a document whose backing version has changed since reconciliation was requested.",
            exception.getMessage());
        var state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(newerBacking, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(originalBacking.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
    }

    @Test
    public void reconciliationRejectsOverlayChangedSinceRequestWithoutChangingState() {
        var originalBacking = snapshot(new DocumentVersion(5), "original backing");
        var expectedOverlay = snapshot(new DocumentVersion(6), "unsaved edits");
        var backing = snapshot(new DocumentVersion(7), "external change");
        var newerOverlay = snapshot(new DocumentVersion(9), "more unsaved edits");
        var resolved = snapshot(new DocumentVersion(10), "resolved edits");

        store.publishBacking(originalBacking);
        store.publishOverlay(expectedOverlay);
        store.publishBacking(backing);
        store.publishOverlay(newerOverlay);
        var originalState = store.state(id).orElseThrow();

        var exception = assertThrows(IllegalStateException.class,
            () -> store.reconcileOverlay(id, backing.version(), expectedOverlay.version(), resolved));

        assertEquals(
            "Cannot reconcile overlay for a document whose overlay version has changed since reconciliation was requested.",
            exception.getMessage());
        var state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(backing, state.backing());
        assertSame(newerOverlay, state.overlay());
        assertSame(newerOverlay, store.current(id).orElseThrow());
        assertEquals(originalBacking.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
    }

    @Test
    public void committedOverlayBecomesBackingWithoutChangingVersion() {
        var backing = snapshot(new DocumentVersion(5), "backing");
        var overlay = snapshot(new DocumentVersion(6), "saved edits");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        var currentBeforeCommit = store.current(id).orElseThrow();

        store.commitOverlay(id, backing.version(), overlay.version());

        var state = store.state(id).orElseThrow();
        assertSame(overlay, state.backing());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(currentBeforeCommit.version(), state.backing().version());
        assertNull(state.overlay());
        assertNull(state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void staleOverlayCannotBeCommittedAndStateRemainsUnchanged() {
        var backing = snapshot(new DocumentVersion(5), "backing");
        var overlay = snapshot(new DocumentVersion(6), "unsaved edits");
        var newerBacking = snapshot(new DocumentVersion(7), "external change");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(newerBacking);
        var originalState = store.state(id).orElseThrow();
        assertTrue(originalState.isStale());

        var exception = assertThrows(IllegalStateException.class,
            () -> store.commitOverlay(id, newerBacking.version(), overlay.version()));

        assertEquals("Cannot commit a stale overlay.", exception.getMessage());
        var state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(newerBacking, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertTrue(state.isStale());
    }

    @Test
    public void equalVersionOverlayWithDifferentContentCannotBeCommitted() {
        var backing = snapshot(new DocumentVersion(5), "backing");
        var overlay = snapshot(new DocumentVersion(5), "different content");

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        var originalState = store.state(id).orElseThrow();
        assertFalse(originalState.isStale());

        var exception = assertThrows(IllegalStateException.class,
            () -> store.commitOverlay(id, backing.version(), overlay.version()));

        assertEquals("Committed overlay must have a newer version than its backing.", exception.getMessage());
        var state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(backing, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
        assertEquals(backing.version(), state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void discardingOverlayRestoresBackingContentAtNewerVersion() {
        var backing = snapshot(new DocumentVersion(5), "int x = 10;");
        var overlay = snapshot(new DocumentVersion(6), "int x = 20;");
        var restoredBacking = snapshot(new DocumentVersion(7), backing.text());

        store.publishBacking(backing);
        store.publishOverlay(overlay);

        store.discardOverlay(id, backing.version(), overlay.version(), restoredBacking);

        var current = store.current(id).orElseThrow();
        assertSame(restoredBacking, current);
        assertEquals(new DocumentVersion(7), current.version());
        assertEquals("int x = 10;", ((TextDocumentSnapshot) current).text());

        var state = store.state(id).orElseThrow();
        assertSame(restoredBacking, state.backing());
        assertNull(state.overlay());
        assertNull(state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void discardingStaleOverlayRestoresLatestBackingContentAndMetadata() {
        var originalBacking = snapshot(new DocumentVersion(5), "int x = 10;");
        var overlay = snapshot(new DocumentVersion(6), "int x = 20;");
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

        var current = store.current(id).orElseThrow();
        assertSame(restoredBacking, current);
        assertEquals(new DocumentVersion(8), current.version());
        assertEquals("int x = 30;", ((TextDocumentSnapshot) current).text());
        assertEquals(updatedBacking.uri(), current.uri());
        assertEquals(updatedBacking.languageId(), current.languageId());
        assertEquals(updatedBacking.origin(), current.origin());
        assertEquals(updatedBacking.encoding(), ((TextDocumentSnapshot) current).encoding());
        var state = store.state(id).orElseThrow();
        assertSame(restoredBacking, state.backing());
        assertNull(state.overlay());
        assertNull(state.baseBackingVersion());
        assertFalse(state.isStale());
    }

    @Test
    public void discardRejectsBackingChangedSinceRequestWithoutChangingState() {
        var backing = snapshot(new DocumentVersion(5), "int x = 10;");
        var overlay = snapshot(new DocumentVersion(6), "int x = 20;");
        var updatedBacking = snapshot(new DocumentVersion(7), "int x = 30;");
        var restoredBacking = snapshot(new DocumentVersion(8), updatedBacking.text());

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishBacking(updatedBacking);
        var originalState = store.state(id).orElseThrow();

        var exception = assertThrows(IllegalStateException.class,
            () -> store.discardOverlay(id, backing.version(), overlay.version(), restoredBacking));

        assertEquals(
            "Cannot discard overlay for a document whose backing version has changed since discard was requested.",
            exception.getMessage());
        var state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(updatedBacking, state.backing());
        assertSame(overlay, state.overlay());
        assertSame(overlay, store.current(id).orElseThrow());
    }

    @Test
    public void discardRejectsOverlayChangedSinceRequestWithoutChangingState() {
        var backing = snapshot(new DocumentVersion(5), "int x = 10;");
        var overlay = snapshot(new DocumentVersion(6), "int x = 20;");
        var updatedOverlay = snapshot(new DocumentVersion(7), "int x = 30;");
        var restoredBacking = snapshot(new DocumentVersion(8), backing.text());

        store.publishBacking(backing);
        store.publishOverlay(overlay);
        store.publishOverlay(updatedOverlay);
        var originalState = store.state(id).orElseThrow();

        var exception = assertThrows(IllegalStateException.class,
            () -> store.discardOverlay(id, backing.version(), overlay.version(), restoredBacking));

        assertEquals(
            "Cannot discard overlay for a document whose overlay version has changed since discard was requested.",
            exception.getMessage());
        var state = store.state(id).orElseThrow();
        assertEquals(originalState, state);
        assertSame(backing, state.backing());
        assertSame(updatedOverlay, state.overlay());
        assertSame(updatedOverlay, store.current(id).orElseThrow());
    }

    @Test
    public void discardRejectsChangesToEachBackingMetadataFieldWithoutChangingState() {
        var backing = snapshot(new DocumentVersion(5), "int x = 10;");
        var overlay = snapshot(new DocumentVersion(6), "int x = 20;");
        var restoredVersion = new DocumentVersion(7);
        var invalidRestorations = List.of(
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
        var originalState = store.state(id).orElseThrow();

        for (var restoredBacking : invalidRestorations) {
            assertThrows(IllegalArgumentException.class,
                () -> store.discardOverlay(id, backing.version(), overlay.version(), restoredBacking));

            var state = store.state(id).orElseThrow();
            assertEquals(originalState, state);
            assertSame(backing, state.backing());
            assertSame(overlay, state.overlay());
            assertSame(overlay, store.current(id).orElseThrow());
        }
    }

    private TextDocumentSnapshot snapshot(DocumentVersion version, String text) {
        return new TextDocumentSnapshot(
            id,
            DocumentUri.inMemory(id),
            version,
            "java",
            text,
            StandardCharsets.UTF_8);
    }
}
