package dev.railroadide.railroad.plugin.spi.services;

import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.spi.state.Cursor;
import dev.railroadide.railroad.plugin.spi.state.Selection;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Service interface for accessing the state of the current document editor.
 * This includes information about cursor positions, text selections, and the language of the document.
 */
public interface DocumentEditorStateService {
    /**
     * Returns a list of all cursor positions in the current text editor.
     *
     * @return a list of {@link Cursor} objects representing cursor positions
     */
    List<Cursor> getCursors();

    /**
     * Returns a list of all text selections in the current text editor.
     *
     * @return a list of {@link Selection} objects representing selected text ranges
     */
    List<Selection> getSelections();

    /**
     * Returns the language identifier of the current document.
     *
     * @return the language ID as a {@link String}
     */
    String getLanguageId();

    /**
     * Returns the active text editor pane, or null if no editor is active.
     *
     * @return the active {@link TextEditorPane}, or null if no editor is active
     */
    @Nullable TextEditorPane getActiveEditorPane();

    /**
     * Sets the active text editor and its associated language ID.
     * This method should be called by the IDE when the active editor changes.
     *
     * @param editor     the active {@link TextEditorPane}, or null if no editor is active
     * @param languageId the language ID of the document in the active editor, or null if unknown
     */
    void setActiveEditor(@Nullable TextEditorPane editor, @Nullable String languageId);
}
