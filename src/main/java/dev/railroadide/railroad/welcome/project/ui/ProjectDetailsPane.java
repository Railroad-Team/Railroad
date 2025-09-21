package dev.railroadide.railroad.welcome.project.ui;

import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.welcome.project.ProjectType;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class ProjectDetailsPane extends ScrollPane {
    private final Map<ProjectType, Node> projectDetailsPanes = new HashMap<>();
    private final ObjectProperty<ProjectType> projectType = new SimpleObjectProperty<>(ProjectType.FABRIC);

    public ProjectDetailsPane() {
        setFitToWidth(true);
        setFitToHeight(true);
        contentProperty().bind(projectTypeProperty().map(this::getOrCreateContentPane));
    }

    public ObjectProperty<ProjectType> projectTypeProperty() {
        return projectType;
    }

    private Node getOrCreateContentPane(@Nullable ProjectType projectType) {
        if (projectType == null) {
            return new RRVBox();
        }

        return this.projectDetailsPanes.computeIfAbsent(projectType, ProjectType::createDetailsPane);
    }
}
