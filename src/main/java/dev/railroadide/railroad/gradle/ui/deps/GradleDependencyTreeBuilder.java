package dev.railroadide.railroad.gradle.ui.deps;

import dev.railroadide.railroad.gradle.ui.GradleTreeBuilder;
import dev.railroadide.railroad.gradle.ui.tree.GradleConfigurationElement;
import dev.railroadide.railroad.gradle.ui.tree.GradleDependencyElement;
import dev.railroadide.railroad.gradle.ui.tree.GradleProjectElement;
import dev.railroadide.railroad.gradle.ui.tree.GradleTreeElement;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroadplugin.dto.RailroadConfiguration;
import dev.railroadide.railroadplugin.dto.RailroadDependency;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;
import org.gradle.tooling.model.DomainObjectSet;

import java.util.*;

public class GradleDependencyTreeBuilder implements GradleTreeBuilder<RailroadConfiguration> {
    @Override
    public TreeItem<GradleTreeElement> buildTree(RailroadProject project, ObservableList<RailroadConfiguration> elements) {
        TreeItem<GradleTreeElement> root = new TreeItem<>();

        List<RailroadConfiguration> rootConfigs = elements.stream()
            .filter(Objects::nonNull)
            .filter(cfg -> cfg.getParent() == null)
            .filter(cfg -> cfg.getDependencies() != null && !cfg.getDependencies().isEmpty())
            .toList();

        Map<RailroadModule, List<RailroadConfiguration>> configsByModule = new HashMap<>();
        for (RailroadConfiguration cfg : elements) {
            if (cfg == null)
                continue;

            RailroadModule module = cfg.getParent();
            if (cfg.getDependencies() == null || cfg.getDependencies().isEmpty())
                continue;

            configsByModule.computeIfAbsent(module, k -> new ArrayList<>()).add(cfg);
        }

        for (Map.Entry<RailroadModule, List<RailroadConfiguration>> entry : configsByModule.entrySet()) {
            RailroadModule module = entry.getKey();
            List<RailroadConfiguration> configs = entry.getValue();

            TreeItem<GradleTreeElement> moduleNode = module != null
                ? new TreeItem<>(new GradleProjectElement(project, module))
                : root;

            if (module != null) {
                root.getChildren().add(moduleNode);
            }

            for (RailroadConfiguration configurationTree : configs) {
                DomainObjectSet<? extends RailroadDependency> dependencies = configurationTree.getDependencies();
                if (dependencies == null || dependencies.isEmpty())
                    continue;

                TreeItem<GradleTreeElement> configurationNode = new TreeItem<>(
                    new GradleConfigurationElement(configurationTree.getName()));
                moduleNode.getChildren().add(configurationNode);

                addDependencies(configurationNode, dependencies);
            }
        }

        for (RailroadConfiguration configurationTree : rootConfigs) {
            DomainObjectSet<? extends RailroadDependency> dependencies = configurationTree.getDependencies();
            if (dependencies == null || dependencies.isEmpty())
                continue;

            TreeItem<GradleTreeElement> configurationNode = new TreeItem<>(
                new GradleConfigurationElement(configurationTree.getName()));
            root.getChildren().add(configurationNode);

            addDependencies(configurationNode, dependencies);
        }

        sortTree(root);
        return root;
    }

    private void addDependencies(TreeItem<GradleTreeElement> parent, Collection<? extends RailroadDependency> dependencies) {
        if (dependencies == null)
            return;

        for (RailroadDependency dependency : dependencies) {
            if (dependency == null)
                continue;

            TreeItem<GradleTreeElement> dependencyNode = new TreeItem<>(new GradleDependencyElement(dependency));
            parent.getChildren().add(dependencyNode);

            addDependencies(dependencyNode, dependency.getChildren());
        }
    }

    private void sortTree(TreeItem<GradleTreeElement> node) {
        Comparator<TreeItem<GradleTreeElement>> comparator = Comparator.comparing(
            item -> {
                GradleTreeElement element = item.getValue();
                return element == null ? "" : element.getName();
            },
            String.CASE_INSENSITIVE_ORDER
        );

        FXCollections.sort(node.getChildren(), comparator);
        for (TreeItem<GradleTreeElement> child : node.getChildren()) {
            sortTree(child);
        }
    }
}
