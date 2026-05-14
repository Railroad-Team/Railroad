package dev.railroadide.railroad.gradle.ui.task;

import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.gradle.ui.GradleTreeBuilder;
import dev.railroadide.railroad.gradle.ui.GradleTreeViewPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroadplugin.dto.RailroadGradleTask;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import dev.railroadide.railroadplugin.dto.RailroadProject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class GradleTasksPane extends GradleTreeViewPane<RailroadGradleTask> {
    public GradleTasksPane(Project project) {
        super(project);
    }

    @Override
    protected GradleTreeBuilder<RailroadGradleTask> createTreeBuilder() {
        return new GradleTaskTreeBuilder();
    }

    @Override
    protected Collection<RailroadGradleTask> getElementsFromModel(GradleModelService modelService, GradleBuildModel model) {
        Optional<GradleBuildModel> cachedModel = modelService.getCachedModel();
        if (cachedModel.isEmpty())
            return List.of();

        RailroadProject gradleProject = cachedModel.get().project();
        List<RailroadGradleTask> tasks = new ArrayList<>();
        for (RailroadModule module : gradleProject.getModules()) {
            tasks.addAll(module.getTasks());
        }

        return tasks;
    }
}
