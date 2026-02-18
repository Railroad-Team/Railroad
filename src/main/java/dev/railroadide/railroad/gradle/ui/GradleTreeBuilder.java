package dev.railroadide.railroad.gradle.ui;

import dev.railroadide.railroad.gradle.ui.tree.GradleTreeElement;
import dev.railroadide.railroad.project.RailroadProject;
import javafx.collections.ObservableList;
import javafx.scene.control.TreeItem;

public interface GradleTreeBuilder<T> {
    TreeItem<GradleTreeElement> buildTree(RailroadProject project, ObservableList<T> elements);

    default String getParentProjectPath(String projectPath) {
        if (projectPath == null || ":".equals(projectPath))
            return null;

        String trimmed = projectPath.startsWith(":") ? projectPath.substring(1) : projectPath;
        if (trimmed.isEmpty())
            return null;

        int lastSeparator = trimmed.lastIndexOf(':');
        if (lastSeparator < 0)
            return ":";

        String parentSegments = trimmed.substring(0, lastSeparator);
        if (parentSegments.isEmpty())
            return ":";

        return ":" + parentSegments;
    }
}
