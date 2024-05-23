package io.github.railroad.project.ui.welcome;

import io.github.railroad.vcs.Repository;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;

public class WelcomeImportProjectsPane extends ScrollPane {
    private final ListView<Repository> projectsList = new ListView<>();

    public WelcomeImportProjectsPane() {

    }
}
