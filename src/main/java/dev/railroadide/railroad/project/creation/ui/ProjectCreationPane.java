package dev.railroadide.railroad.project.creation.ui;

import dev.railroadide.core.project.ProjectContext;
import dev.railroadide.core.project.ProjectData;
import dev.railroadide.core.ui.RRBorderPane;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.utility.javafx.TextAreaOutputStream;
import dev.railroadide.railroad.welcome.WelcomePane;
import javafx.application.Platform;
import javafx.concurrent.Service;
import javafx.scene.control.ButtonType;
import lombok.Getter;

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
            ex -> showErrorAndReturnToWelcome(
                "railroad.project.creation.error.title",
                "railroad.project.creation.error.header",
                "railroad.project.creation.error.content",
                ex != null ? ex.getMessage() : null
            )
        );

        setCenter(view);
        service.start();
    }

    protected void openInIDE(ProjectContext ctx) {
        Platform.runLater(() -> {
            try {
                var project = new Project(ctx.projectDir(), ctx.data().getAsString(ProjectData.DefaultKeys.NAME));
                Railroad.switchToIDE(project);
            } catch (Exception exception) {
                Railroad.LOGGER.error("Failed to open project in IDE", exception);
                showErrorAndReturnToWelcome("railroad.project.creation.error.open_ide.title",
                    "railroad.project.creation.error.open_ide.header",
                    "railroad.project.creation.error.open_ide.content",
                    null);
            }
        });
    }

    protected void showErrorAndReturnToWelcome(String titleKey, String headerKey, String contentKey, String additionalInfo) {
        Platform.runLater(() -> {
            String title = L18n.localize(titleKey);
            String header = L18n.localize(headerKey);
            String content = L18n.localize(contentKey);

            if (additionalInfo != null) {
                content += "\n\n" + additionalInfo;
            }

            Railroad.showErrorAlert(title, header, content, buttonType -> {
                if (buttonType == ButtonType.OK) {
                    returnToWelcome();
                }
            });
        });
    }

    protected void returnToWelcome() {
        Platform.runLater(() -> getScene().setRoot(new WelcomePane()));
    }
}
