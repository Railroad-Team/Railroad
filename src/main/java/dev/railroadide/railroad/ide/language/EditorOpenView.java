package dev.railroadide.railroad.ide.language;

import dev.railroadide.railroad.ide.ui.MarkdownPreviewPane;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
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
    /**
     * Returns the current source editor, including one created lazily by a Markdown view.
     *
     * @return current text editor, or null for a preview without a source editor
     */
    public @Nullable TextEditorPane activeEditor() {
        return content instanceof MarkdownPreviewPane markdown ? markdown.getMarkdownEditorPane() : activeEditor;
    }

    /**
     * Observes source editor creation for views that initially show only a preview.
     *
     * @return observable current source editor
     */
    public ObservableValue<TextEditorPane> activeEditorProperty() {
        return content instanceof MarkdownPreviewPane markdown
            ? markdown.editorProperty()
            : new ReadOnlyObjectWrapper<>(activeEditor).getReadOnlyProperty();
    }
}
