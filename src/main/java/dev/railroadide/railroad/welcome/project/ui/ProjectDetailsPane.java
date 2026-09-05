package dev.railroadide.railroad.welcome.project.ui;

import dev.railroadide.railroad.project.ProjectType;
import dev.railroadide.railroad.project.ProjectTypeRegistry;
import dev.railroadide.railroad.ui.RRVBox;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Scrollable onboarding content that creates and caches one interface per selected project type. */
public class ProjectDetailsPane extends ScrollPane {
    private final Map<ProjectType, Node> projectDetailsPanes = new HashMap<>();
    private final ObjectProperty<ProjectType> projectType = new SimpleObjectProperty<>(ProjectTypeRegistry.FABRIC);

    /** Creates a pane whose content follows the selected project type, initially Fabric. */
    public ProjectDetailsPane() {
        setFitToWidth(true);
        setFitToHeight(true);
        contentProperty().bind(projectTypeProperty().map(this::getOrCreateContentPane));
    }

    /**
     * Exposes the selected type driving the content binding. Previously created interfaces are reused.
     *
     * @return the writable project-type property, initially set to Fabric
     */
    public ObjectProperty<ProjectType> projectTypeProperty() {
        return projectType;
    }

    private Node getOrCreateContentPane(@Nullable ProjectType projectType) {
        if (projectType == null)
            return new RRVBox();

        return this.projectDetailsPanes.computeIfAbsent(projectType, ProjectType::createOnboardingUI);
    }
}
