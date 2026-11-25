package dev.railroadide.railroad.ide.ui.navigation.quickopen;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.ui.codeeditor.TextEditorPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import javafx.application.Platform;
import javafx.scene.Node;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Supplies and opens the items listed by a {@link GoToPopup}.
 *
 * @param <T> item type
 */
public interface GoToContributor<T> {
    /**
     * Returns the translation key of the popup title.
     *
     * @return title key
     */
    String titleKey();

    /**
     * Collects the items of a project. Called off the JavaFX thread.
     *
     * @param project project to search
     * @param activityCheck throws {@link java.util.concurrent.CancellationException} once the popup has closed
     * @return items to search
     * @throws Exception when the items cannot be collected
     */
    List<T> collect(Project project, Runnable activityCheck) throws Exception;

    /**
     * Returns the name matched by patterns without a path separator.
     *
     * @param item item
     * @return item name
     */
    String name(T item);

    /**
     * Returns the full path matched by patterns containing {@link #separator()}.
     *
     * @param item item
     * @return item path
     */
    String path(T item);

    /**
     * Returns the secondary text shown after the name, such as the containing directory or package.
     *
     * @param item item
     * @return location text, or an empty string
     */
    String detail(T item);

    /**
     * Returns the separator between path segments.
     *
     * @return path separator
     */
    char separator();

    /**
     * Rewrites a typed pattern into the form of {@link #path(Object)}, for example by normalizing separators.
     *
     * @param pattern typed pattern
     * @return normalized pattern
     */
    default String normalizePattern(String pattern) {
        return pattern;
    }

    /**
     * Creates the icon shown before an item.
     *
     * @param item item
     * @return icon node
     */
    Node icon(T item);

    /**
     * Opens an item. Called on the JavaFX thread after the popup has closed.
     *
     * @param item chosen item
     * @param query query that found it, including any line suffix
     */
    void open(T item, GoToSearch.Query query);

    /**
     * Opens a file in the editor and, once its editor is shown, moves the caret to the offset chosen from its text.
     *
     * @param file file to open
     * @param caret chooses the caret offset from the editor text, returning {@code -1} to leave the caret; or
     *            {@code null} to only open the file
     */
    static void openInEditor(Path file, @Nullable ToIntFunction<String> caret) {
        Services.EDITOR_TAB_MANAGER.open(file);
        if (caret == null)
            return;

        Path normalized = file.toAbsolutePath().normalize();
        Platform.runLater(() -> Services.EDITOR_TAB_MANAGER.activeTab()
            .filter(tab -> tab.path().toAbsolutePath().normalize().equals(normalized))
            .map(tab -> tab.view().activeEditor())
            .ifPresent(editor -> moveCaret(editor, caret.applyAsInt(editor.getText()))));
    }

    private static void moveCaret(TextEditorPane editor, int offset) {
        if (offset < 0 || offset > editor.getLength())
            return;

        editor.showSelection(offset, offset);
        editor.requestFocus();
    }
}
