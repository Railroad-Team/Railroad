package dev.railroadide.railroad.ide.ui.git.commit.changes;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseEvent;

import java.util.function.Consumer;

/**
 * Supplies presentation and interaction behavior for an entry in the commit changes tree.
 */
public interface ChangeItem {
    /**
     * Creates the icon representing this change-tree entry.
     *
     * @return item icon node, or null when no icon is needed
     */
    Node getIcon();

    /**
     * Returns the primary title of the change-tree entry.
     *
     * @return item title
     */
    String getTitle();

    /**
     * Returns secondary information shown alongside the title.
     *
     * @return subtitle, or null when no secondary text is available
     */
    String getSubtitle();

    /**
     * Creates the repository actions available for this entry.
     *
     * @param project project whose files and workspace are being displayed
     * @return item context menu, or null when no actions are available
     */
    ContextMenu getContextMenu(Project project);

    /**
     * Returns the callback for checkbox selection changes.
     *
     * @return selection callback, or null when selection requires no action
     */
    Consumer<Boolean> getSelectionHandler();

    /**
     * Returns the callback for opening or acting on this entry.
     *
     * @return double-click callback, or null when no action is available
     */
    Consumer<MouseEvent> getDoubleClickHandler();

    /**
     * Returns the CSS class used to style this change entry.
     *
     * @return style class, or null when no additional styling is required
     */
    String getStyleClass();

    /**
     * Appends a nonempty subtitle to a title in parentheses.
     *
     * @param title primary item title
     * @param subtitle optional secondary text to append in parentheses
     * @return combined title, or the original title when the subtitle is absent
     */
    static String formatTitle(String title, String subtitle) {
        return subtitle == null || subtitle.isEmpty()
            ? title
            : title + " (" + subtitle + ")";
    }
}
