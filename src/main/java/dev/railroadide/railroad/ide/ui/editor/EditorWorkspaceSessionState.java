package dev.railroadide.railroad.ide.ui.editor;

import java.util.List;
import java.util.Objects;

/**
 * Stores the versioned editor layout, detached windows, and open document tabs.
 *
 * @param schemaVersion format version of the saved workspace state
 * @param mainLayout layout in the main IDE window, or null for the default group
 * @param detachedWindows saved detached editor windows
 * @param tabs saved states of open editor tabs
 */
public record EditorWorkspaceSessionState(
    int schemaVersion,
    EditorLayoutNodeState mainLayout,
    List<DetachedEditorWindowState> detachedWindows,
    List<EditorTabSessionState> tabs
) {
    /**
     * Workspace session format version supported by this implementation.
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    /**
     * Creates workspace state, copying non-null entries and supplying a default main group when needed.
     *
     * @param schemaVersion format version of the saved workspace state
     * @param mainLayout layout in the main IDE window, or null for the default group
     * @param detachedWindows saved detached editor windows
     * @param tabs saved states of open editor tabs
     */
    public EditorWorkspaceSessionState {
        detachedWindows = detachedWindows == null
            ? List.of()
            : detachedWindows.stream()
                .filter(Objects::nonNull)
                .toList();
        tabs = tabs == null
            ? List.of()
            : tabs.stream()
                .filter(Objects::nonNull)
                .toList();
        if (mainLayout == null) {
            mainLayout = EditorLayoutNodeState.group(EditorTabSessionState.DEFAULT_EDITOR_GROUP_ID, null);
        }
    }

    /**
     * Wraps tab states in a workspace with one default main group and no detached windows.
     *
     * @param tabs saved states of open editor tabs
     * @return workspace state in the current schema version
     */
    public static EditorWorkspaceSessionState legacy(List<EditorTabSessionState> tabs) {
        return new EditorWorkspaceSessionState(
            CURRENT_SCHEMA_VERSION,
            EditorLayoutNodeState.group(EditorTabSessionState.DEFAULT_EDITOR_GROUP_ID, null),
            List.of(),
            tabs);
    }

    /**
     * Checks whether this state uses the current workspace schema.
     *
     * @return true when the schema version matches CURRENT_SCHEMA_VERSION
     */
    public boolean isSupported() {
        return schemaVersion == CURRENT_SCHEMA_VERSION;
    }
}
