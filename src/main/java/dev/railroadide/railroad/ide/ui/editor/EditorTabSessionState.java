package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.ide.sst.document.api.DocumentId;
import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.sst.document.api.DocumentUri;

import java.nio.file.Path;
import java.util.Objects;

/** Serializable state required to reconstruct one editor tab in a project session. */
public record EditorTabSessionState(
    DocumentIdentity identity,
    Path path,
    int order,
    boolean pinned,
    boolean preview,
    boolean active,
    String editorGroupId) {
    public static final String DEFAULT_EDITOR_GROUP_ID = "railroad:editor-group:main";

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
    }

    public EditorTabSessionState(
        Path path,
        int order,
        boolean pinned,
        boolean preview,
        boolean active,
        String editorGroupId) {
        this(null, path, order, pinned, preview, active, editorGroupId);
    }

    public static EditorTabSessionState legacy(Path path, int order, boolean active) {
        return new EditorTabSessionState(path, order, false, false, active, DEFAULT_EDITOR_GROUP_ID);
    }
}
