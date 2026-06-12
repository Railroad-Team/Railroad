package dev.railroadide.railroad.gradle.ui.task;

import dev.railroadide.railroad.gradle.ui.GradleTreeBuilder;
import dev.railroadide.railroad.gradle.ui.tree.GradleModuleElement;
import dev.railroadide.railroad.gradle.ui.tree.GradleTaskElement;
import dev.railroadide.railroad.gradle.ui.tree.GradleTaskGroupElement;
import dev.railroadide.railroad.gradle.ui.tree.GradleTreeElement;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.utility.StringUtils;
import dev.railroadide.railroadplugin.dto.RailroadGradleTask;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import javafx.collections.FXCollections;
import javafx.scene.control.TreeItem;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GradleTaskTreeBuilder implements GradleTreeBuilder<RailroadGradleTask> {
    @Override
    public TreeItem<GradleTreeElement> buildTree(Project project, List<RailroadGradleTask> elements) {
        TreeItem<GradleTreeElement> root = new TreeItem<>();

        Map<RailroadModule, List<RailroadGradleTask>> tasksByProject = elements.stream()
            .collect(Collectors.groupingBy(RailroadGradleTask::module));

        Map<String, RailroadModule> projectsByPath = tasksByProject.keySet().stream()
            .collect(Collectors.toMap(RailroadModule::getPath, Function.identity()));

        Map<String, TreeItem<GradleTreeElement>> projectNodes = new ConcurrentHashMap<>();

        for (RailroadModule module : tasksByProject.keySet()) {
            ensureProjectNode(project, module, projectsByPath, projectNodes, root);
        }

        for (Map.Entry<RailroadModule, List<RailroadGradleTask>> entry : tasksByProject.entrySet()) {
            TreeItem<GradleTreeElement> projectNode = projectNodes.get(entry.getKey().getPath());
            if (projectNode == null)
                continue;

            addTasksToProjectNode(project, projectNode, entry.getValue());
        }

        sortTree(root);
        return root;
    }

    private TreeItem<GradleTreeElement> ensureProjectNode(
        Project project,
        RailroadModule module,
        Map<String, RailroadModule> projectsByPath,
        Map<String, TreeItem<GradleTreeElement>> projectNodes,
        TreeItem<GradleTreeElement> root
    ) {
        return projectNodes.computeIfAbsent(module.getPath(), path -> {
            TreeItem<GradleTreeElement> parentNode = root;
            String parentPath = getParentProjectPath(path);
            if (parentPath != null) {
                RailroadModule parentProject = projectsByPath.get(parentPath);
                if (parentProject != null) {
                    parentNode = ensureProjectNode(project, parentProject, projectsByPath, projectNodes, root);
                }
            }

            TreeItem<GradleTreeElement> node =
                new TreeItem<>(new GradleModuleElement(project, module));
            parentNode.getChildren().add(node);
            return node;
        });
    }

    private void addTasksToProjectNode(Project project, TreeItem<GradleTreeElement> projectNode,
                                       List<RailroadGradleTask> projectTasks) {
        Map<String, List<RailroadGradleTask>> tasksByGroup = projectTasks.stream()
            .collect(Collectors.groupingBy(task -> {
                String group = task.getGroup();
                return group == null ? "<no-group>" : StringUtils.capitalizeFirstLetterOfEachWord(group);
            }, HashMap::new, Collectors.toList()));

        for (Map.Entry<String, List<RailroadGradleTask>> groupEntry : tasksByGroup.entrySet()) {
            String groupName = groupEntry.getKey();
            List<RailroadGradleTask> groupTasks = groupEntry.getValue();

            TreeItem<GradleTreeElement> groupNode = new TreeItem<>(
                new GradleTaskGroupElement(groupName));
            projectNode.getChildren().add(groupNode);

            for (RailroadGradleTask task : groupTasks) {
                TreeItem<GradleTreeElement> taskNode =
                    new TreeItem<>(new GradleTaskElement(project, task));
                groupNode.getChildren().add(taskNode);
            }
        }
    }

    private void sortTree(TreeItem<GradleTreeElement> node) {
        Comparator<TreeItem<GradleTreeElement>> comparator =
            Comparator.<TreeItem<GradleTreeElement>, Integer>comparing(
                item -> typeRank(item.getValue())
            ).thenComparing(
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

    private int typeRank(GradleTreeElement element) {
        if (element instanceof GradleTaskElement)
            return 0;

        if (element instanceof GradleTaskGroupElement)
            return 1;

        if (element instanceof GradleModuleElement)
            return 2;

        return Integer.MAX_VALUE;
    }
}
