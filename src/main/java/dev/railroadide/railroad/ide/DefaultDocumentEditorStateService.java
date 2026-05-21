package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.spi.services.DocumentEditorStateService;
import dev.railroadide.railroad.plugin.spi.state.Cursor;
import dev.railroadide.railroad.plugin.spi.state.Selection;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DefaultDocumentEditorStateService implements DocumentEditorStateService {
    private TextEditorPane activeEditorPane;
    private String activeLanguageId = "";

    @Override
    public void setActiveEditor(@Nullable TextEditorPane editor, @Nullable String languageId) {
        this.activeEditorPane = editor;
        this.activeLanguageId = editor != null && languageId != null ? languageId : "";
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
        return activeLanguageId;
    }

    @Override
    public TextEditorPane getActiveEditorPane() {
        return activeEditorPane;
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
}

