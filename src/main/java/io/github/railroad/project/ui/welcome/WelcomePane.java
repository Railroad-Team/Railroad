package io.github.railroad.project.ui.welcome;

import io.github.railroad.settings.ui.SettingsPane;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Separator;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class WelcomePane extends SplitPane {
    private final WelcomeLeftPane leftPane;
    private final WelcomeHeaderPane headerPane;
    private final WelcomeProjectsPane projectsPane;

    public WelcomePane() {
        leftPane = new WelcomeLeftPane();
        headerPane = new WelcomeHeaderPane();
        projectsPane = new WelcomeProjectsPane(headerPane.getSearchField());
        projectsPane.setSortProperty(headerPane.getSortComboBox().valueProperty());

        leftPane.setPrefWidth(200);
        leftPane.setMinWidth(200);

        headerPane.setPrefHeight(80);
        projectsPane.setPrefHeight(500);

        var rightPane = new VBox(headerPane, new Separator(), projectsPane);
        VBox.setVgrow(projectsPane, Priority.ALWAYS);
        rightPane.setPrefWidth(600);
        rightPane.setMinWidth(600);

        setOrientation(Orientation.HORIZONTAL);
        getItems().addAll(leftPane, rightPane);

        SplitPane.setResizableWithParent(leftPane, false);
        SplitPane.setResizableWithParent(rightPane, true);

        leftPane.getListView().getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            switch (newValue) {
                case HOME -> {
                    if(getChildren().get(1) != rightPane) {
                        getItems().set(1, rightPane);
                    }
                }
                case SETTINGS -> {
                    var settingsPane = new SettingsPane();
                    Scene scene = getScene();
                    scene.setRoot(settingsPane);
                    settingsPane.getBackButton().setOnAction(event -> {
                        scene.setRoot(WelcomePane.this);
                        leftPane.getListView().getSelectionModel().select(oldValue);
                    });
                }
            }
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