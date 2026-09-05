package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import javafx.scene.Node;
import org.jspecify.annotations.Nullable;

/**
 * Describes opened editor content and its optional active text editor.
 *
 * @param content the JavaFX node displayed for the opened file
 * @param activeEditor the active text editor, or {@code null} for non-text content
 * @param languageId the stable language identifier
 */
public record EditorOpenView(
    Node content,
    @Nullable TextEditorPane activeEditor,
    String languageId
) {
}
