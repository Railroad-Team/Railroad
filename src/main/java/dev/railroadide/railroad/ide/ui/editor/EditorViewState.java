package dev.railroadide.railroad.ide.ui.editor;

import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import org.jspecify.annotations.Nullable;

public record EditorViewState(
    int caretPosition,
    int anchorPosition) {
    public static final EditorViewState EMPTY = new EditorViewState(0, 0);

    static EditorViewState capture(@Nullable TextEditorPane editor) {
        if (editor == null)
            return EMPTY;

        return new EditorViewState(
            editor.getCaretPosition(),
            editor.getAnchor());
    }
}
