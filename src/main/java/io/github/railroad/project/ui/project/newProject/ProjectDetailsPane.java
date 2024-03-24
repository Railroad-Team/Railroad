package io.github.railroad.project.ui.project.newProject;

import io.github.railroad.project.ProjectType;
import io.github.railroad.project.ui.project.newProject.details.FabricProjectDetailsPane;
import io.github.railroad.project.ui.project.newProject.details.ForgeProjectDetailsPane;
import io.github.railroad.project.ui.project.newProject.details.NeoForgedProjectDetailsPane;
import io.github.railroad.project.ui.project.newProject.details.QuiltProjectDetailsPane;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ProjectDetailsPane extends ScrollPane {
    private final Map<ProjectType, Node> projectDetailsPanes = new HashMap<>();
    private final ObjectProperty<ProjectType> projectType = new SimpleObjectProperty<>(ProjectType.FORGE);

    public ProjectDetailsPane() {
        setContent(getProjectDetailsPane(ProjectType.FORGE));

        this.projectType.addListener((observable, oldValue, newValue) -> {
            switch (newValue) {
                case FORGE -> setContent(getProjectDetailsPane(ProjectType.FORGE));
                case FABRIC -> setContent(getProjectDetailsPane(ProjectType.FABRIC));
                case NEOFORGED -> setContent(getProjectDetailsPane(ProjectType.NEOFORGED));
                case QUILT -> setContent(getProjectDetailsPane(ProjectType.QUILT));
            }
        });
    }

    public ObjectProperty<ProjectType> projectTypeProperty() {
        return projectType;
    }

    private Node getProjectDetailsPane(@NotNull ProjectType projectType) {
        return this.projectDetailsPanes.computeIfAbsent(projectType, k -> switch (projectType) {
            case FORGE -> new ForgeProjectDetailsPane();
            case FABRIC -> new FabricProjectDetailsPane();
            case NEOFORGED -> new NeoForgedProjectDetailsPane();
            case QUILT -> new QuiltProjectDetailsPane();
        });
    }
}
