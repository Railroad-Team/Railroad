package dev.railroadide.railroad.gradle.ui.tree;

import dev.railroadide.railroad.gradle.ui.task.GradleTaskContextMenu;
import javafx.scene.control.TreeCell;
import org.kordamp.ikonli.javafx.FontIcon;

public class GradleTreeCell extends TreeCell<GradleTreeElement> {
    private final FontIcon icon = new FontIcon();

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
                        var runConfiguration = GradleTaskContextMenu.getOrCreateRunConfig(
                            taskElement.getProject(),
                            taskElement.getTask());
                        runConfiguration.run(taskElement.getProject());
                    }
                }
            });
        }
    }
}
