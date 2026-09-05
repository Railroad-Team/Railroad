package dev.railroadide.railroad.project.onboarding.creation.ui;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.project.ProjectContext;
import dev.railroadide.railroad.project.ProjectData;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.ui.RRBorderPane;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.utility.javafx.TextAreaOutputStream;
import dev.railroadide.railroad.welcome.WelcomePane;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.stage.Stage;
import lombok.Getter;

import java.io.IOException;

/**
 * Hosts project creation progress and connects service completion to opening the new project in the IDE.
 * Construct and initialize this pane on the JavaFX application thread.
 */
@Getter
public class ProjectCreationPane extends RRBorderPane {
    protected final ProjectContext context;
    protected final ProjectCreationView view;
    protected final TextAreaOutputStream taos;

    /**
     * Creates a context and progress view for a project beneath its configured parent directory.
     *
     * @param data project data containing the parent path and project name
     */
    public ProjectCreationPane(ProjectData data) {
        this.context = new ProjectContext(
            data,
            data.getAsPath(ProjectData.DefaultKeys.PATH).resolve(data.getAsString(ProjectData.DefaultKeys.NAME)));

        this.view = new ProjectCreationView(data);
        this.taos = new TextAreaOutputStream(view.getLogArea());

        Services.UI_MANAGER.assignWhileAttached(UIIds.ProjectOnboarding.PROJECT_CREATION, this);
    }

    /**
     * Displays the progress view, installs cancellation and completion handlers, and starts the service.
     * Successful completion opens the project; failure presents an error dialog.
     *
     * @param service creation service ready to be started on the JavaFX application thread
     */
    public void initService(Service<?> service) {
        view.bindToService(
            service,
            service::cancel,
            () -> openInIDE(context),
            exception -> WindowBuilder.createExceptionAlert(
                "railroad.project.creation.error.title",
                "railroad.project.creation.error.header",
                exception,
                () -> {
                    try {
                        taos.close();
                    } catch (IOException exception1) {
                        Railroad.LOGGER.error("Failed to close TextAreaOutputStream", exception1);
                    }

                    ((Stage) view.sceneProperty().get().getWindow()).close();
                    returnToWelcome();
                }));

        setCenter(view);
        service.start();
    }

    protected void openInIDE(ProjectContext ctx) {
        Platform.runLater(() -> {
            try {
                var project = new RailroadProject(ctx.projectDir(),
                    ctx.data().getAsString(ProjectData.DefaultKeys.NAME));
                project.open(null);
            } catch (Exception exception) {
                Railroad.LOGGER.error("Failed to open project in IDE", exception);

                WindowBuilder.createExceptionAlert(
                    "railroad.project.creation.error.open_ide.title",
                    "railroad.project.creation.error.open_ide.header",
                    exception,
                    ProjectCreationPane::returnToWelcome);
            }
        });
    }

    protected static void returnToWelcome() {
        Platform.runLater(() -> Railroad.WINDOW_MANAGER.getPrimaryStage().getScene().setRoot(new WelcomePane()));
    }
}
