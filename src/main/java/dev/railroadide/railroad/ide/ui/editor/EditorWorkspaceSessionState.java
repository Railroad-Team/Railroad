package dev.railroadide.railroad.ide.ui.editor;

import java.util.List;
import java.util.Objects;

public record EditorWorkspaceSessionState(
    int schemaVersion,
    EditorLayoutNodeState mainLayout,
    List<DetachedEditorWindowState> detachedWindows,
    List<EditorTabSessionState> tabs) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

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

    public static EditorWorkspaceSessionState legacy(List<EditorTabSessionState> tabs) {
        return new EditorWorkspaceSessionState(
            CURRENT_SCHEMA_VERSION,
            EditorLayoutNodeState.group(EditorTabSessionState.DEFAULT_EDITOR_GROUP_ID, null),
            List.of(),
            tabs);
    }

    public boolean isSupported() {
        return schemaVersion == CURRENT_SCHEMA_VERSION;
    }
}
