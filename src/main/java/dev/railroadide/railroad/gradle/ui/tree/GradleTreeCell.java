package dev.railroadide.railroad.gradle.ui.tree;

import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.CommandDispatcher;
import dev.railroadide.railroad.command.GradleCommands;
import javafx.scene.control.TreeCell;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Renders a Gradle tree element's name, icon, tooltip, and context menu.
 * Double-clicking a task cell runs the corresponding Gradle task.
 */
public class GradleTreeCell extends TreeCell<GradleTreeElement> {
    private final FontIcon icon = new FontIcon();

    /**
     * Creates a tree cell with a 16-pixel icon.
     */
    public GradleTreeCell() {
        super();
        icon.setIconSize(16);
    }

    @Override
    protected void updateItem(GradleTreeElement item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            setText(item.getName());
            icon.getStyleClass().removeIf(styleClass -> styleClass.equals("gradle-project-element") ||
                styleClass.equals("gradle-tasks-group-element") ||
                styleClass.equals("gradle-task-element"));
            icon.getStyleClass().add(item.getStyleClass());
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
