package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Stores the identity, placement, and view state needed to reopen a closed editor tab.
 *
 * @param identity logical identity of the document
 * @param path path of the document file
 * @param pinned whether the tab is pinned against automatic eviction
 * @param preview whether the tab occupies the reusable preview slot
 * @param editorGroupId identifier of the editor group containing the tab
 * @param previousIndex zero-based tab position before closing
 * @param viewState selection, scroll, and fold state to restore
 */
public record ClosedEditorTab(
    DocumentIdentity identity,
    Path path,
    boolean pinned,
    boolean preview,
    String editorGroupId,
    int previousIndex,
    EditorViewState viewState
) {
    /**
     * Creates a closed-tab snapshot with a normalized absolute path and nonnegative previous index.
     *
     * @param identity logical identity of the document
     * @param path path of the document file
     * @param pinned whether the tab is pinned against automatic eviction
     * @param preview whether the tab occupies the reusable preview slot
     * @param editorGroupId identifier of the editor group containing the tab
     * @param previousIndex zero-based tab position before closing
     * @param viewState selection, scroll, and fold state to restore
     */
    public ClosedEditorTab {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(editorGroupId, "editorGroupId must not be null");
        Objects.requireNonNull(viewState, "viewState must not be null");

        path = path.toAbsolutePath().normalize();

        if (previousIndex < 0) {
            previousIndex = 0;
        }
    }

    /**
     * Returns the closed document's logical identifier.
     *
     * @return document identity key
     */
    public DocumentId documentId() {
        return identity.id();
    }

    /**
     * Captures a tab's document, placement, and current editor view state.
     *
     * @param tab editor tab to act on
     * @param index zero-based tab position in its editor group
     * @return snapshot suitable for reopening the tab
     */
    public static ClosedEditorTab capture(EditorTab tab, int index) {
        return new ClosedEditorTab(
            tab.identity(),
            tab.path(),
            tab.pinned(),
            tab.preview(),
            tab.editorGroupId(),
            index,
            EditorViewState.capture(tab.view().activeEditor()));
    }
}
