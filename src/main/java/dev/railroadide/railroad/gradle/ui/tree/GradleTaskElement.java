package dev.railroadide.railroad.gradle.ui.tree;

import dev.railroadide.railroad.gradle.ui.task.GradleTaskContextMenu;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroadplugin.dto.RailroadGradleTask;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.devicons.Devicons;

/**
 * Represents a Gradle task with its description as a tooltip and run and debug context menu actions.
 */
@Getter
public class GradleTaskElement extends GradleTreeElement {
    private final Project project;
    private final RailroadGradleTask task;

    /**
     * Creates a tree element displaying the supplied task's name.
     *
     * @param project the Railroad project used to run or debug the task
     * @param task the Gradle task represented by this element
     * @throws IllegalArgumentException if {@code project} or {@code task} is null
     */
    public GradleTaskElement(Project project, RailroadGradleTask task) {
        super(task != null ? task.getName() : "Unknown Task");
        if (project == null)
            throw new IllegalArgumentException("Project cannot be null");
        if (task == null)
            throw new IllegalArgumentException("Task cannot be null");

        this.project = project;
        this.task = task;
    }

    @Override
    public Ikon getIcon() {
        return Devicons.TERMINAL;
    }

    @Override
    public String getStyleClass() {
        return "gradle-task-element";
    }

    @Override
    public Tooltip getTooltip() {
        String description = this.task.getDescription();
        if (description == null || description.isEmpty())
            return null;

        return new Tooltip(description);
    }

    @Override
    public ContextMenu getContextMenu() {
        return new GradleTaskContextMenu(this.project, this.task);
    }
}
