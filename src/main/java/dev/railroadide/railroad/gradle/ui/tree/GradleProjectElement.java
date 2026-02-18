package dev.railroadide.railroad.gradle.ui.tree;

import dev.railroadide.railroad.gradle.ui.GradleProjectContextMenu;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.utility.icon.RailroadBrandsIcon;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import javafx.scene.control.ContextMenu;
import org.kordamp.ikonli.Ikon;

import java.util.Objects;

public class GradleProjectElement extends GradleTreeElement {
    private final RailroadProject project;
    private final RailroadModule module;

    public GradleProjectElement(RailroadProject project, RailroadModule module) {
        super(Objects.requireNonNull(module, "module must not be null").getName() == null
            ? "Unnamed Project"
            : Objects.requireNonNull(module, "module must not be null").getName());

        this.project = project;
        this.module = Objects.requireNonNull(module, "module must not be null");
    }

    @Override
    public Ikon getIcon() {
        return RailroadBrandsIcon.GRADLE;
    }

    @Override
    public String getStyleClass() {
        return "gradle-project-element";
    }

    @Override
    public ContextMenu getContextMenu() {
        return new GradleProjectContextMenu(this.project, this.module);
    }
}
