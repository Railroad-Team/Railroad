package io.github.railroad.project.ui.welcome;

import io.github.railroad.Railroad;
import io.github.railroad.project.ui.project.newProject.NewProjectPane;
import io.github.railroad.settings.ui.SettingsPane;
import io.github.railroad.ui.defaults.RRSeparator;
import io.github.railroad.ui.defaults.RRSplitPane;
import javafx.geometry.Orientation;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class WelcomePane extends RRSplitPane {
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

        var rightPane = new VBox(headerPane, new RRSeparator(), projectsPane);
        VBox.setVgrow(projectsPane, Priority.ALWAYS);

        setOrientation(Orientation.HORIZONTAL);
        getItems().addAll(leftPane, rightPane);

        SplitPane.setResizableWithParent(leftPane, false);
        SplitPane.setResizableWithParent(rightPane, true);

        leftPane.setMinWidth(100);
        rightPane.setMinWidth(300);

        setDividerPositions((1.0/3));

        // Setup buttons
        leftPane.getListView()
                .getSelectionModel()
                .selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> {
            switch (newValue) {
                case NEW_PROJECT -> {
                    var newProjectPane = this.newProjectPane.updateAndGet(
                            pane -> Objects.requireNonNullElseGet(pane, NewProjectPane::new));
                    Railroad.getScene().setRoot(newProjectPane);
                    newProjectPane.getBackButton().setOnAction(e ->
                            Railroad.getScene().setRoot(WelcomePane.this));
                }
                case OPEN_PROJECT -> System.out.println("[Open Project] is still not implemented!");
                case IMPORT_PROJECT -> System.out.println("[Import project] is still not implemented!");

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