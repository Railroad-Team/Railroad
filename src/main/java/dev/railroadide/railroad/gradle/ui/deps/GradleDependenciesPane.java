package dev.railroadide.railroad.gradle.ui.deps;

import dev.railroadide.railroad.gradle.model.GradleBuildModel;
import dev.railroadide.railroad.gradle.service.GradleModelService;
import dev.railroadide.railroad.gradle.ui.GradleTreeBuilder;
import dev.railroadide.railroad.gradle.ui.GradleTreeViewPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroadplugin.dto.RailroadConfiguration;
import dev.railroadide.railroadplugin.dto.RailroadProject;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class GradleDependenciesPane extends GradleTreeViewPane<RailroadConfiguration> {
    public GradleDependenciesPane(Project project) {
        super(project);
    }

    @Override
    protected GradleTreeBuilder<RailroadConfiguration> createTreeBuilder() {
        return new GradleDependencyTreeBuilder();
    }

    @Override
    protected Collection<RailroadConfiguration> getElementsFromModel(GradleModelService modelService, GradleBuildModel model) {
        return List.copyOf(modelService.getCachedModel()
            .map(GradleBuildModel::project)
            .map(RailroadProject::getModules)
            .map(Collection::stream)
            .orElseGet(Stream::empty)
            .flatMap(module -> module.getConfigurations().stream())
            .toList());
    }
}
