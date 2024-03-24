package io.github.railroad.project.ui.welcome;

import javafx.geometry.Orientation;
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
        projectsPane = new WelcomeProjectsPane();
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
        SplitPane.setResizableWithParent(rightPane, false);
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