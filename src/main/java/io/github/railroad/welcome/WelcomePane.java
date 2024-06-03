package io.github.railroad.welcome;

import io.github.railroad.Railroad;
import io.github.railroad.project.ProjectManager;
import io.github.railroad.project.data.Project;
import io.github.railroad.project.ui.create.NewProjectPane;
import io.github.railroad.settings.ui.SettingsPane;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;
import lombok.Getter;

import javax.swing.filechooser.FileSystemView;
import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.railroad.ui.BrowseButton.folderBrowser;

public class WelcomePane extends RRHBox {
    @Getter
    private final WelcomeLeftPane leftPane;
    @Getter
    private final WelcomeHeaderPane headerPane;
    @Getter
    private final WelcomeProjectsPane projectsPane;
    private final WelcomeImportProjectsPane importProjectsPane;

    private final AtomicReference<NewProjectPane> newProjectPane = new AtomicReference<>();
    private final AtomicReference<SettingsPane> settingsPane = new AtomicReference<>();

    public WelcomePane() {
        leftPane = new WelcomeLeftPane();
        headerPane = new WelcomeHeaderPane();
        projectsPane = new WelcomeProjectsPane(headerPane.getSearchField());
        projectsPane.setSortProperty(headerPane.getSortComboBox().valueProperty());
        importProjectsPane = new WelcomeImportProjectsPane();
        headerPane.setPrefHeight(80);

        var rightPane = new RRVBox();
        rightPane.setPadding(new Insets(10));
        var horizontalSeparator = new Separator();
        horizontalSeparator.setPadding(new Insets(10, 0, 10, -10));
        rightPane.getChildren().addAll(headerPane, horizontalSeparator, projectsPane);
        RRVBox.setVgrow(projectsPane, Priority.ALWAYS);
        RRVBox.setVgrow(importProjectsPane, Priority.ALWAYS);
        var verticalSeparator = new Separator(Orientation.VERTICAL);
        verticalSeparator.setPadding(new Insets(0));
        getChildren().addAll(leftPane, verticalSeparator, rightPane);
        RRHBox.setHgrow(rightPane, Priority.ALWAYS);

        rightPane.setMinWidth(300);

        // Setup buttons
        leftPane.getListView()
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
                    if (newValue == null)
                        return;

                    Platform.runLater(() -> {
                        switch (newValue) {
                            case HOME -> {
                                rightPane.getChildren().clear();
                                rightPane.getChildren().addAll(headerPane, new Separator(), projectsPane);
                            }
                            case NEW_PROJECT -> {
                                var newProjectPane = this.newProjectPane.updateAndGet(
                                        pane -> Objects.requireNonNullElseGet(pane, NewProjectPane::new));
                                Railroad.getScene().setRoot(newProjectPane);
                                newProjectPane.getBackButton().setOnAction(e ->
                                        Railroad.getScene().setRoot(WelcomePane.this));
                            }
                            case OPEN_PROJECT -> {
                                var directoryChooser = folderBrowser(FileSystemView.getFileSystemView().getHomeDirectory(), "Open Project");
                                //TODO Create/import/whatever with the selected folder here
                                File selected = directoryChooser.showDialog(getScene().getWindow());
                                Railroad.LOGGER.debug("Dir Selected: {}\n", selected);

                                if(selected != null) {
                                    Railroad.PROJECT_MANAGER.newProject(new Project(selected.toPath()));
                                }
                            }
                            case IMPORT_PROJECT -> {
                                rightPane.getChildren().clear();
                                rightPane.getChildren().addAll(headerPane, new Separator(), importProjectsPane);
                            }

                            case SETTINGS -> {
                                var settingsPane = this.settingsPane.updateAndGet(
                                        pane -> Objects.requireNonNullElseGet(pane, SettingsPane::new));
                                Railroad.getScene().setRoot(settingsPane);
                                settingsPane.getBackButton().setOnAction(e ->
                                        Railroad.getScene().setRoot(WelcomePane.this));
                            }

                            default -> throw new IllegalStateException("Unexpected value: " + newValue);
                        }
                        leftPane.getListView().getSelectionModel().clearSelection();
                    });
                });
    }
}