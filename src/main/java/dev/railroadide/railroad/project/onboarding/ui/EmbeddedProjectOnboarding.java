package dev.railroadide.railroad.project.onboarding.ui;

import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import java.util.function.Consumer;
import dev.railroadide.railroad.project.onboarding.creation.ui.ProjectCreationPane;

/** Hosts a project wizard directly beside the project-type selector. */
public final class EmbeddedProjectOnboarding extends BorderPane {
    private boolean started;

    /**
     * Creates a wizard host that starts onboarding once it is first attached to a scene.
     * Setup views appear in the center of this pane; project creation views replace the scene root.
     *
     * @param start action that starts onboarding using the supplied callback to display its views
     */
    public EmbeddedProjectOnboarding(Consumer<Consumer<Parent>> start) {
        setMinSize(0, 0);
        sceneProperty().addListener((observable, previous, scene) -> {
            if (scene != null && !started) {
                started = true;
                start.accept(view -> {
                    if (view instanceof ProjectCreationPane) {
                        // Creation owns the workspace until it completes or is cancelled.
                        getScene().setRoot(view);
                    } else {
                        setCenter(view);
                    }
                });
            }
        });
    }
}
