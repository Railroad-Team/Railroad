package dev.railroadide.railroad.gradle.ui.task;

import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.gradle.ui.GradleTreeBuilder;
import dev.railroadide.railroad.gradle.ui.GradleTreeViewPane;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroadplugin.dto.RailroadGradleTask;
import dev.railroadide.railroadplugin.dto.RailroadModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class GradleTasksPane extends GradleTreeViewPane<RailroadGradleTask> {
    public GradleTasksPane(RailroadProject project) {
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

        dev.railroadide.railroadplugin.dto.RailroadProject railroadProject = cachedModel.get().project();
        List<RailroadGradleTask> tasks = new ArrayList<>();
        for (RailroadModule module : railroadProject.getModules()) {
            tasks.addAll(module.getTasks());
        }

        return tasks;
    }
}
