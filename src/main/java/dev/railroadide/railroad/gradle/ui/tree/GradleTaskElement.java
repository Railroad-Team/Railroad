package dev.railroadide.railroad.gradle.ui.tree;

import dev.railroadide.railroad.gradle.ui.task.GradleTaskContextMenu;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroadplugin.dto.RailroadGradleTask;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.devicons.Devicons;

@Getter
public class GradleTaskElement extends GradleTreeElement {
    private final Project project;
    private final RailroadGradleTask task;

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
