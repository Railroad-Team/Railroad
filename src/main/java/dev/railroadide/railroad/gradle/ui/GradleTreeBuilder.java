package dev.railroadide.railroad.gradle.ui;

import dev.railroadide.railroad.gradle.ui.tree.GradleTreeElement;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import javafx.scene.control.TreeItem;

import java.util.List;

/**
 * Builds a display tree from elements of a Gradle model.
 *
 * @param <T> the model element type used to build the tree
 */
public interface GradleTreeBuilder<T> {
    /**
     * Builds a tree for the supplied project and model elements.
     *
     * @param project the Railroad project that owns the model elements
     * @param elements the model elements to organize into the tree
     * @return the root item whose children contain the display tree
     */
    TreeItem<GradleTreeElement> buildTree(Project project, List<T> elements);

    /**
     * Finds the parent of a colon-separated Gradle project path.
     *
     * @param projectPath the project path, with or without a leading colon, or {@code null}
     * @return the parent path with a leading colon, {@code ":"} for a top-level subproject,
     *         or {@code null} for a null, empty, or root path
     */
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
