package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Serializable state required to reconstruct one editor tab in a project session.
 *
 * @param identity logical identity of the document
 * @param path path of the document file
 * @param order zero-based position in the saved tab order
 * @param pinned whether the tab is pinned against automatic eviction
 * @param preview whether the tab occupies the reusable preview slot
 * @param active whether this document should be activated on restoration
 * @param editorGroupId identifier of the editor group containing the tab
 * @param viewState selection, scroll, and fold state to restore
 */
public record EditorTabSessionState(
    DocumentIdentity identity,
    Path path,
    int order,
    boolean pinned,
    boolean preview,
    boolean active,
    String editorGroupId,
    EditorViewState viewState
) {
    /**
     * Stable identifier of the primary editor group.
     */
    public static final String DEFAULT_EDITOR_GROUP_ID = "railroad:editor-group:main";

    /**
     * Creates tab session state, normalizing its path, order, group, and optional view state.
     *
     * @param identity logical identity of the document
     * @param path path of the document file
     * @param order zero-based position in the saved tab order
     * @param pinned whether the tab is pinned against automatic eviction
     * @param preview whether the tab occupies the reusable preview slot
     * @param active whether this document should be activated on restoration
     * @param editorGroupId identifier of the editor group containing the tab
     * @param viewState selection, scroll, and fold state to restore
     */
    public EditorTabSessionState {
        if (identity == null && path == null)
            throw new IllegalArgumentException("Document identity or path must be present");
        if (path != null) {
            path = path.toAbsolutePath().normalize();
        }
        if (identity == null) {
            identity = new DocumentIdentity(DocumentId.create(), DocumentUri.fromPath(path));
        }
        order = Math.max(0, order);
        if (editorGroupId == null || editorGroupId.isBlank()) {
            editorGroupId = DEFAULT_EDITOR_GROUP_ID;
        } else {
            editorGroupId = editorGroupId.trim();
        }
        viewState = Objects.requireNonNullElse(viewState, EditorViewState.EMPTY);
    }

    /**
     * Creates tab session state, normalizing its path, order, group, and optional view state.
     *
     * @param path path of the document file
     * @param order zero-based position in the saved tab order
     * @param pinned whether the tab is pinned against automatic eviction
     * @param preview whether the tab occupies the reusable preview slot
     * @param active whether this document should be activated on restoration
     * @param editorGroupId identifier of the editor group containing the tab
     */
    public EditorTabSessionState(
        Path path,
        int order,
        boolean pinned,
        boolean preview,
        boolean active,
        String editorGroupId
    ) {
        this(null, path, order, pinned, preview, active, editorGroupId, EditorViewState.EMPTY);
    }

    /**
     * Creates tab session state, normalizing its path, order, group, and optional view state.
     *
     * @param identity logical identity of the document
     * @param path path of the document file
     * @param order zero-based position in the saved tab order
     * @param pinned whether the tab is pinned against automatic eviction
     * @param preview whether the tab occupies the reusable preview slot
     * @param active whether this document should be activated on restoration
     * @param editorGroupId identifier of the editor group containing the tab
     */
    public EditorTabSessionState(
        DocumentIdentity identity,
        Path path,
        int order,
        boolean pinned,
        boolean preview,
        boolean active,
        String editorGroupId
    ) {
        this(identity, path, order, pinned, preview, active, editorGroupId, EditorViewState.EMPTY);
    }

    /**
     * Creates legacy session state in the default editor group with no pin or preview flag.
     *
     * @param path path of the document file
     * @param order zero-based position in the saved tab order
     * @param active whether this document should be activated on restoration
     * @return tab session state with an empty editor view state
     */
    public static EditorTabSessionState legacy(Path path, int order, boolean active) {
        return new EditorTabSessionState(path, order, false, false, active, DEFAULT_EDITOR_GROUP_ID);
    }
}
