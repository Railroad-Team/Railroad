package dev.railroadide.railroad.plugin.spi.editor;

import dev.railroadide.railroad.ide.sst.document.api.TextDocumentSnapshot;
import java.util.List;
import java.util.function.Consumer;

/**
 * Source-text operations on one open editor, independent of its language and UI toolkit.
 * Call operations on the editor UI thread. Immutable snapshots may be used on any thread.
 * Observers run on the UI thread after the current editor transition settles; intermediate
 * revisions can be coalesced. Retained handles cannot edit a closed or reopened editor.
 * Source features should reveal before capturing a snapshot: initializing a lazy editor
 * can publish a new revision, including the editor's existing line-ending normalization.
 * Coordinates always refer to the returned snapshot, not raw file bytes.
 */
public interface TextEditorAccess {
    /** Reveals source, creating a lazy source editor if necessary. */
    void reveal();

    /**
     * Returns the canonical published current snapshot, including an unsaved conflict overlay.
     * Does not initialize lazy source. Throws IllegalStateException if closed or not reconciled.
     */
    TextDocumentSnapshot snapshot();

    /** Returns the directed selection in UTF-16 offsets, or (0, 0) before lazy source exists. */
    TextSelection selection();

    /** Validates the expected snapshot and bounds, then selects and reveals source. */
    EditResult selectAndReveal(TextDocumentSnapshot expected, TextSelection selection);

    /** Reports effective editability; replacement rechecks this advisory value. */
    boolean isEditable();

    /**
     * Validates the entire batch before mutation, using original snapshot offsets in any order.
     * Overlaps and multiple edits at the same start offset are invalid. Adjacent ranges are allowed.
     * Successful nonempty changes form one undo step isolated from surrounding typing.
     * Empty batches and text-identical replacements succeed without changing history or revision.
     * This does not save or resolve external conflicts. No cross-document transaction is implied.
     */
    EditResult replace(TextDocumentSnapshot expected, List<TextReplacement> replacements);

    /**
     * Subscribes before scheduling initial delivery, then delivers changed canonical snapshots.
     * Hidden backing changes with an unchanged overlay and unrelated documents are filtered.
     * Observation alone must not create a lazy source. Close the subscription when no longer needed.
     */
    EditorSubscription observe(Consumer<TextDocumentSnapshot> listener);

    /** Observes the initial and subsequent settled selection without creating lazy source. */
    EditorSubscription observeSelection(Consumer<TextSelection> listener);
}
