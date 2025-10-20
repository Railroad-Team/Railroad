package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.ide.ui.JavaCodeEditorPane;
import dev.railroadide.railroad.ide.ui.JsonCodeEditorPane;
import dev.railroadide.railroad.ide.ui.TextEditorPane;
import dev.railroadide.railroadpluginapi.services.DocumentEditorStateService;
import dev.railroadide.railroadpluginapi.state.Cursor;
import dev.railroadide.railroadpluginapi.state.Selection;

import java.util.List;

public class DefaultDocumentEditorStateService implements DocumentEditorStateService {
    private TextEditorPane activeEditorPane;

    public void setActiveEditorPane(TextEditorPane activeEditorPane) {
        this.activeEditorPane = activeEditorPane;
    }

    private Cursor getCursorFromPosition(String text, int position) {
        int line = 0;
        int column = 0;

        for (int i = 0; i < position && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }

        return new Cursor(line, column);
    }

    @Override
    public List<Cursor> getCursors() {
        if (activeEditorPane != null) {
            int caretPosition = activeEditorPane.getCaretPosition();
            return List.of(getCursorFromPosition(activeEditorPane.getText(), caretPosition));
        }

        return List.of();
    }

    @Override
    public List<Selection> getSelections() {
        if (activeEditorPane != null) {
            int start = activeEditorPane.getSelection().getStart();
            int end = activeEditorPane.getSelection().getEnd();

            if (start != end) {
                Cursor startCursor = getCursorFromPosition(activeEditorPane.getText(), start);
                Cursor endCursor = getCursorFromPosition(activeEditorPane.getText(), end);
                return List.of(new Selection(startCursor, endCursor));
            }
        }

        return List.of();
    }

    @Override
    public String getLanguageId() {
        if (activeEditorPane instanceof JavaCodeEditorPane) {
            return ((JavaCodeEditorPane) activeEditorPane).getLanguageId();
        } else if (activeEditorPane instanceof JsonCodeEditorPane) {
            return ((JsonCodeEditorPane) activeEditorPane).getLanguageId();
        }

        return "";
    }
}

