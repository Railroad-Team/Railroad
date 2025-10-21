package dev.railroadide.railroad.welcome;

import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.settings.ui.SettingsPane;
import dev.railroadide.railroad.welcome.imports.WelcomeImportProjectsPane;
import dev.railroadide.railroad.welcome.project.ui.NewProjectPane;
import dev.railroadide.railroad.window.AlertType;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.application.Platform;
import javafx.geometry.Insets;
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

public class WelcomePane extends HBox {
    @Getter
    private final WelcomeLeftPane leftPane;
    @Getter
    private final WelcomeHeaderPane headerPane;
    @Getter
    private final WelcomeProjectsPane projectsPane;
    private final AtomicReference<NewProjectPane> newProjectPane = new AtomicReference<>();

    public WelcomePane() {
        setSpacing(0);
        setPadding(new Insets(0));

        leftPane = new WelcomeLeftPane();
        headerPane = new WelcomeHeaderPane();
        projectsPane = new WelcomeProjectsPane(headerPane.getSearchField());
        projectsPane.setSortProperty(headerPane.getSortComboBox().valueProperty());
        headerPane.setPrefHeight(80);

        var rightPane = new RRVBox(18);
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
    }

    private void openProjectDialog() {
        Platform.runLater(() -> {
            var directoryChooser = new DirectoryChooser();
            directoryChooser.setTitle(L18n.localize("railroad.dialog.open_project.title"));
            directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));

            File selectedDirectory = directoryChooser.showDialog(getScene().getWindow());
            if (selectedDirectory != null) {
                Path projectPath = selectedDirectory.toPath();

                if (isValidProjectDirectory(projectPath)) {
                    var project = new Project(projectPath);
                    project.open();
                } else {
                    WindowBuilder.createAlert(
                        AlertType.ERROR,
                        "railroad.dialog.open_project.error.invalid_directory",
                        "railroad.dialog.open_project.error.invalid_directory",
                        "railroad.dialog.open_project.error.invalid_directory.message"
                    ).build();
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
}
