package dev.railroadide.railroad.gradle.ui.tree;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Represents a folder grouping related tasks in the Gradle task tree.
 */
public class GradleTaskGroupElement extends GradleTreeElement {
    /**
     * Creates a task group element, displaying the ungrouped-task marker as {@code "Other"}.
     *
     * @param name the group display name, or {@code "<no-group>"} for ungrouped tasks
     */
    public GradleTaskGroupElement(String name) {
        super("<no-group>".equals(name) ? "Other" : name);
    }

    @Override
    public Ikon getIcon() {
        return FontAwesomeSolid.FOLDER;
    }

    @Override
    public String getStyleClass() {
        return "gradle-tasks-group-element";
    }
}
