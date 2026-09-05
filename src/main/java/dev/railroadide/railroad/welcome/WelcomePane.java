package dev.railroadide.railroad.welcome;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.settings.ui.SettingsPane;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.welcome.imports.WelcomeImportProjectsPane;
import dev.railroadide.railroad.welcome.project.ui.NewProjectPane;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import lombok.Getter;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Root welcome view coordinating project browsing, creation, import, directory opening, and settings actions. */
public class WelcomePane extends HBox {
    /**
     * Sidebar controlling the visible welcome content.
     *
     * @return the navigation sidebar
     */
    @Getter
    private final WelcomeLeftPane leftPane;
    /**
     * Header containing search and sorting controls for the home view.
     *
     * @return the home header
     */
    @Getter
    private final WelcomeHeaderPane headerPane;
    /**
     * Known-project browser displayed by the home view.
     *
     * @return the project browser
     */
    @Getter
    private final WelcomeProjectsPane projectsPane;
    private final AtomicReference<NewProjectPane> newProjectPane = new AtomicReference<>();

    /**
     * Builds the home view, connects search and sort controls, and installs sidebar navigation handlers.
     * Registers the welcome UI identifier while attached and schedules an initial focus request.
     */
    public WelcomePane() {
        getStyleClass().add("welcome-root");

        leftPane = new WelcomeLeftPane();
        headerPane = new WelcomeHeaderPane();
        projectsPane = new WelcomeProjectsPane(headerPane.getSearchField());
        projectsPane.setSortProperty(headerPane.getSortComboBox().valueProperty());
        headerPane.getStyleClass().add("welcome-header-pane");

        var rightPane = new RRVBox();
        rightPane.getStyleClass().addAll("welcome-right-pane", "welcome-right-pane-content");
        rightPane.getChildren().addAll(headerPane, projectsPane);
        VBox.setVgrow(projectsPane, Priority.ALWAYS);
        rightPane.setMaxWidth(Double.MAX_VALUE);

        var verticalSeparator = new Separator(Orientation.VERTICAL);
        verticalSeparator.getStyleClass().add("welcome-vertical-separator");

        getChildren().addAll(leftPane, verticalSeparator, rightPane);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        leftPane.getListView().getSelectionModel().selectedItemProperty().addListener((_, _, newValue) -> {
            if (newValue == null)
                return;
            switch (newValue) {
                case HOME -> {
                    rightPane.getChildren().clear();
                    rightPane.getChildren().addAll(headerPane, projectsPane);
                }
                case OPEN_PROJECT -> {
                    openProjectDialog();
                    // Reset selection to HOME after opening dialog
                    leftPane.getListView().getSelectionModel().select(WelcomeLeftPane.MenuType.HOME);
                }
                case NEW_PROJECT -> {
                    newProjectPane.set(new NewProjectPane());
                    rightPane.getChildren().setAll(newProjectPane.get());
                    VBox.setVgrow(newProjectPane.get(), Priority.ALWAYS);
                }
                case IMPORT_PROJECT -> {
                    var importProjectsPane = new WelcomeImportProjectsPane();
                    rightPane.getChildren().setAll(importProjectsPane);
                    VBox.setVgrow(importProjectsPane, Priority.ALWAYS);
                }
                case SETTINGS -> {
                    SettingsPane.openSettingsWindow();
                    leftPane.getListView().getSelectionModel().select(WelcomeLeftPane.MenuType.HOME);
                }
                default -> throw new IllegalStateException("Unexpected value: " + newValue);
            }
        });

        Platform.runLater(this::requestFocus);
        Services.UI_MANAGER.assignWhileAttached(UIIds.Welcome.WELCOME, this);
    }

    private void openProjectDialog() {
        Platform.runLater(() -> {
            var directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle(L18n.localize("railroad.dialog.open_project.title"));
            directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));

            File selectedDirectory = directoryChooser.showDialog(getScene().getWindow());
            if (selectedDirectory != null) {
                Path projectPath = selectedDirectory.toPath();

                // For now, we will allow opening any directory and let the Project class handle validation
                // TODO: Re-add validation here in the future
                // if (isValidProjectDirectory(projectPath)) {
                var project = new RailroadProject(projectPath);
                project.open(null);
                // } else {
                // WindowBuilder.createAlert(
                // AlertType.ERROR,
                // "railroad.dialog.open_project.error.invalid_directory",
                // "railroad.dialog.open_project.error.invalid_directory",
                // "railroad.dialog.open_project.error.invalid_directory.message"
                // ).build();
                // }
            }
        });
    }

    private boolean isValidProjectDirectory(Path path) {
        // Check for common project indicators
        return path.resolve("build.gradle").toFile().exists() ||
            path.resolve("pom.xml").toFile().exists() ||
            path.resolve("gradle.properties").toFile().exists() ||
            path.resolve("src").toFile().exists() ||
            path.resolve("build").toFile().exists();
    }
}
