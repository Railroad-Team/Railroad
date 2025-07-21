package dev.railroadide.railroad.welcome;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.settings.ui.SettingsPane;
import dev.railroadide.railroad.welcome.imports.WelcomeImportProjectsPane;
import dev.railroadide.railroad.welcome.project.ui.NewProjectPane;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import lombok.Getter;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public class WelcomePane extends HBox {
    @Getter
    private final WelcomeLeftPane leftPane;
    @Getter
    private final WelcomeHeaderPane headerPane;
    @Getter
    private final WelcomeProjectsPane projectsPane;

    private final AtomicReference<NewProjectPane> newProjectPane = new AtomicReference<>();
    private static Stage settingsStage = null;

    public WelcomePane() {
        setSpacing(0);
        setPadding(new Insets(0));

        leftPane = new WelcomeLeftPane();
        headerPane = new WelcomeHeaderPane();
        projectsPane = new WelcomeProjectsPane(headerPane.getSearchField());
        projectsPane.setSortProperty(headerPane.getSortComboBox().valueProperty());
        headerPane.setPrefHeight(80);

        var rightPane = new VBox(18);
        rightPane.setPadding(new Insets(18, 24, 18, 24));
        rightPane.getStyleClass().add("welcome-right-pane");
        rightPane.getChildren().addAll(headerPane, projectsPane);
        VBox.setVgrow(projectsPane, Priority.ALWAYS);
        rightPane.setMinWidth(340);
        rightPane.setMaxWidth(Double.MAX_VALUE);

        var verticalSeparator = new Separator(Orientation.VERTICAL);
        verticalSeparator.setPadding(new Insets(0, 0, 0, 0));
        verticalSeparator.setPrefWidth(1);

        getChildren().addAll(leftPane, verticalSeparator, rightPane);
        HBox.setHgrow(rightPane, Priority.ALWAYS);

        // Setup buttons
        leftPane.getListView().getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) return;
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
                    rightPane.getChildren().clear();
                    rightPane.getChildren().addAll(headerPane, newProjectPane.get());
                }
                case IMPORT_PROJECT -> {
                    var importProjectsPane = new WelcomeImportProjectsPane();
                    rightPane.getChildren().setAll(importProjectsPane);
                    VBox.setVgrow(importProjectsPane, Priority.ALWAYS);
                }
                case SETTINGS -> {
                    openSettingsWindow();
                    leftPane.getListView().getSelectionModel().select(WelcomeLeftPane.MenuType.HOME);
                }
                default -> throw new IllegalStateException("Unexpected value: " + newValue);
            }
        });

        Platform.runLater(() -> requestFocus());
    }

    private void openProjectDialog() {
        Platform.runLater(() -> {
            DirectoryChooser directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle(L18n.localize("railroad.dialog.open_project.title"));
            directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));
            
            File selectedDirectory = directoryChooser.showDialog(getScene().getWindow());
            if (selectedDirectory != null) {
                Path projectPath = selectedDirectory.toPath();
                
                // Check if the selected directory contains a valid project
                if (isValidProjectDirectory(projectPath)) {
                    Project project = new Project(projectPath);
                    project.open();
                } else {
                    // Show error dialog for invalid project directory
                    Railroad.showErrorAlert(
                        L18n.localize("railroad.dialog.open_project.error.invalid_directory"), 
                        L18n.localize("railroad.dialog.open_project.error.invalid_directory"), 
                        L18n.localize("railroad.dialog.open_project.error.invalid_directory.message")
                    );
                }
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

    /**
     * Opens the settings window in a new stage.
     * If the settings window is already open, it will be brought to the front.
     * The window is automatically cleaned up when closed.
     */
    public void openSettingsWindow() {
        Platform.runLater(() -> {
            if (settingsStage == null || !settingsStage.isShowing()) {
                settingsStage = new Stage();
                settingsStage.setTitle("Settings");
                var settingsPane = new SettingsPane();
                var scene = new Scene(settingsPane, 900, 600);
                Railroad.handleStyles(scene);
                settingsStage.setScene(scene);
                settingsStage.setOnHidden($ -> settingsStage = null);
                settingsStage.show();
            } else {
                settingsStage.toFront();
            }
        });
    }
}