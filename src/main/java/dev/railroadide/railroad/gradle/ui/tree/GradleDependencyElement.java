package dev.railroadide.railroad.gradle.ui.tree;

import dev.railroadide.railroadplugin.dto.RailroadDependency;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Represents a Gradle dependency, displaying its group, name, and version as dependency coordinates.
 */
@Getter
public class GradleDependencyElement extends GradleTreeElement {
    private final RailroadDependency dependencyNode;

    /**
     * Creates a tree element for the supplied dependency.
     *
     * @param dependencyNode the non-null dependency represented by this element
     */
    public GradleDependencyElement(@NotNull RailroadDependency dependencyNode) {
        super(dependencyNode.getGroup() + ":" + dependencyNode.getName() + ":" + dependencyNode.getVersion());
        this.dependencyNode = dependencyNode;
    }

    @Override
    public Ikon getIcon() {
        return FontAwesomeSolid.BOOK;
    }

    @Override
    public String getStyleClass() {
        return "gradle-dependency-element";
    }

}
