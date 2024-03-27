package io.github.railroad.settings.ui.general;

import javafx.scene.layout.VBox;

public class SettingsGeneralPane extends VBox {
    // GitHub connection
    private final GithubConnectionPane githubConnectionPane = new GithubConnectionPane();

    public SettingsGeneralPane() {
        getChildren().add(githubConnectionPane);
    }
}
