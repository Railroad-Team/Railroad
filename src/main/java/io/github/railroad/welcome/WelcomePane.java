package io.github.railroad.welcome;

import io.github.railroad.Railroad;
import io.github.railroad.project.ui.create.NewProjectPane;
import io.github.railroad.settings.ui.SettingsPane;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Separator;
import javafx.scene.layout.Priority;

import javax.swing.filechooser.FileSystemView;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.railroad.ui.BrowseButton.folderBrowser;

public class WelcomePane extends RRHBox {
    private final WelcomeLeftPane leftPane;
    private final WelcomeHeaderPane headerPane;
    private final WelcomeProjectsPane projectsPane;

    private final AtomicReference<NewProjectPane> newProjectPane = new AtomicReference<>();
    private final AtomicReference<SettingsPane> settingsPane = new AtomicReference<>();

    public WelcomePane() {
        leftPane = new WelcomeLeftPane();
        headerPane = new WelcomeHeaderPane();
        projectsPane = new WelcomeProjectsPane(headerPane.getSearchField());
        projectsPane.setSortProperty(headerPane.getSortComboBox().valueProperty());

        headerPane.setPrefHeight(80);

        var rightPane = new RRVBox();
        rightPane.setPadding(new Insets(10));
        var horizontalSeparator = new Separator();
        horizontalSeparator.setPadding(new Insets(10, 0, 10, -10));
        rightPane.getChildren().addAll(headerPane, horizontalSeparator, projectsPane);
        RRVBox.setVgrow(projectsPane, Priority.ALWAYS);

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
                                Railroad.LOGGER.debug("Dir Selected: {}\n", directoryChooser.showDialog(getScene().getWindow()));
                            }
                            case IMPORT_PROJECT -> {
                                Railroad.LOGGER.warn("[Import project] is still not implemented!");
                                //TODO Either create an import pane with options for java ver, mc ver, forge/fabric etc OR have open dir & automagically work it out and maybe check with the user
                                //TODO That all of the values are correct?
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

    public WelcomeProjectsPane getProjectsPane() {
        return projectsPane;
    }

    public WelcomeHeaderPane getHeaderPane() {
        return headerPane;
    }

    public WelcomeLeftPane getLeftPane() {
        return leftPane;
    }
}