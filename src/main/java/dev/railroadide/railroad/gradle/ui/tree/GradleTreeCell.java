package dev.railroadide.railroad.gradle.ui.tree;

import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.CommandDispatcher;
import dev.railroadide.railroad.command.GradleCommands;
import javafx.scene.control.TreeCell;
import javafx.scene.Node;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Renders a Gradle tree element's name, icon, tooltip, and context menu.
 * Double-clicking a task cell runs the corresponding Gradle task.
 */
public class GradleTreeCell extends TreeCell<GradleTreeElement> {
    private final FontIcon icon = new FontIcon();
    private String elementStyleClass;

    /**
     * Creates a tree cell with a 16-pixel icon.
     */
    public GradleTreeCell() {
        super();
        icon.setIconSize(16);
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        Node disclosure = getDisclosureNode();
        if (disclosure != null && disclosure.isVisible()) {
            disclosure.relocate(disclosure.getLayoutX(),
                snapPositionY((getHeight() - disclosure.getLayoutBounds().getHeight()) / 2));
        }
    }

    @Override
    protected void updateItem(GradleTreeElement item, boolean empty) {
        super.updateItem(item, empty);
        icon.getStyleClass().remove(elementStyleClass);
        elementStyleClass = null;
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            setTooltip(null);
            setContextMenu(null);
            setOnMouseClicked(null);
        } else {
            setText(item.getName());
            elementStyleClass = item.getStyleClass();
            icon.getStyleClass().add(elementStyleClass);
            icon.setIconCode(item.getIcon());
            setGraphic(icon);
            setTooltip(item.getTooltip());
            setContextMenu(item.getContextMenu());
            setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !isEmpty()) {
                    if (item instanceof GradleTaskElement taskElement) {
                        CommandDispatcher.execute(GradleCommands.RUN_TASK, CommandContext.withArgument(
                            taskElement.getProject(), this, taskElement.getTask()));
                    }
                }
            });
        }
    }
}
