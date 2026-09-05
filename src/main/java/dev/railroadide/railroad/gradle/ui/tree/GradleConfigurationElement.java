package dev.railroadide.railroad.gradle.ui.tree;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Represents a named Gradle configuration in the dependency tree.
 */
public class GradleConfigurationElement extends GradleTreeElement {
    /**
     * Creates a configuration element with the supplied display name.
     *
     * @param name the Gradle configuration name
     */
    public GradleConfigurationElement(String name) {
        super(name);
    }

    @Override
    public Ikon getIcon() {
        return FontAwesomeSolid.FOLDER;
    }

    @Override
    public String getStyleClass() {
        return "gradle-configuration-element";
    }
}
