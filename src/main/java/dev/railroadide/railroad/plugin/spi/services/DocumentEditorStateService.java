package dev.railroadide.railroad.plugin.spi.services;

import dev.railroadide.railroad.plugin.spi.editor.EditorSubscription;
import dev.railroadide.railroad.plugin.spi.editor.TextEditorAccess;
import dev.railroadide.railroad.plugin.spi.state.Cursor;
import dev.railroadide.railroad.plugin.spi.state.Selection;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Service interface for accessing the state of the current document editor.
 * The active target follows managed document activation, independently of keyboard focus.
 * Call methods and close subscriptions on the editor UI thread.
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
     * Returns the active document's text capability without creating a lazy source view.
     * Non-text documents and an empty workspace return absence.
     *
     * @return optional active text capability
     */
    Optional<TextEditorAccess> getActiveTextEditor();

    /**
     * Observes active text targets. The listener receives the current target immediately,
     * then each target change on the editor UI thread. Closing the subscription is idempotent
     * and prevents further callbacks, including callbacks from an ongoing notification.
     *
     * @param listener receiver of the optional active text capability
     * @return registration owned by the caller
     */
    EditorSubscription observeActiveTextEditor(Consumer<Optional<TextEditorAccess>> listener);

    /**
     * Sets the active text editor and its associated language ID.
     * This method should be called by the IDE when the active editor changes.
     *
     * @param editor the active text capability, or null if no text editor is active
     * @param languageId the language ID of the document in the active editor, or null if unknown
     */
    void setActiveEditor(@Nullable TextEditorAccess editor, @Nullable String languageId);
}
