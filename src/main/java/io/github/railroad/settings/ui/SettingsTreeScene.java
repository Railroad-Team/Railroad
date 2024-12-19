package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SettingsTreeScene {
    private Stage stage;
    public SettingsTreeScene() {
        stage = new Stage();

        stage.setTitle("Settings");

        var tree = Railroad.SETTINGS_MANAGER.createTree();

        var scene = new Scene(tree, 500, 700);

        stage.setScene(scene);
        stage.show();
        Railroad.SETTINGS_MANAGER.loadFromFile();
    }

    public void close() {
        stage.close();
    }
}