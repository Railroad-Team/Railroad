package dev.railroadide.railroad.project.creation.ui;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.ui.RRBorderPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.IDESetup;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.utility.javafx.TextAreaOutputStream;
import dev.railroadide.railroad.welcome.WelcomePane;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.stage.Stage;
import lombok.Getter;

import java.io.IOException;

@Getter
public class ProjectCreationPane extends RRBorderPane {
    protected final ProjectContext context;
    protected final ProjectCreationView view;
    protected final TextAreaOutputStream taos;

    public ProjectCreationPane(ProjectData data) {
        this.context = new ProjectContext(
            data,
            data.getAsPath(ProjectData.DefaultKeys.PATH).resolve(data.getAsString(ProjectData.DefaultKeys.NAME))
        );

        this.view = new ProjectCreationView(data);
        this.taos = new TextAreaOutputStream(view.getLogArea());
    }

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
                }
            )
        );

        setCenter(view);
        service.start();
    }

    protected void openInIDE(ProjectContext ctx) {
        Platform.runLater(() -> {
            try {
                var project = new Project(ctx.projectDir(), ctx.data().getAsString(ProjectData.DefaultKeys.NAME));
                IDESetup.switchToIDE(project);
            } catch (Exception exception) {
                Railroad.LOGGER.error("Failed to open project in IDE", exception);

                WindowBuilder.createExceptionAlert(
                    "railroad.project.creation.error.open_ide.title",
                    "railroad.project.creation.error.open_ide.header",
                    exception,
                    ProjectCreationPane::returnToWelcome
                );
            }
        });
    }

    protected static void returnToWelcome() {
        Platform.runLater(() -> Railroad.WINDOW_MANAGER.getPrimaryStage().getScene().setRoot(new WelcomePane()));
    }
}
