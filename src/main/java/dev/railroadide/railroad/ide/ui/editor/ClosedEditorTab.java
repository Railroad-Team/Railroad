package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.ide.sst.document.api.DocumentIdentity;
import dev.railroadide.railroad.ide.sst.document.api.DocumentId;

import java.nio.file.Path;
import java.util.Objects;

public record ClosedEditorTab(
    DocumentIdentity identity,
    Path path,
    boolean pinned,
    boolean preview,
    String editorGroupId,
    int previousIndex,
    EditorViewState viewState
) {
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

    public DocumentId documentId() {
        return identity.id();
    }

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
