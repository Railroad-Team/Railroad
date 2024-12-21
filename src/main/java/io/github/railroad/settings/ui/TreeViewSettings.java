package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.localization.ui.LocalizedButton;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class TreeViewSettings {
    private Stage stage;

    public TreeViewSettings() {
        stage = new Stage();

        stage.setTitle("Settings");

        var vbox = new VBox();

        var tree = Railroad.SETTINGS_HANDLER.createTree();
        var button = new LocalizedButton("railroad.generic.apply");
        button.setOnAction(event -> Railroad.SETTINGS_HANDLER.saveSettingsFile());

        vbox.getChildren().addAll(tree, button);
        var scene = new Scene(vbox, 600, 700);

        stage.setScene(scene);
        stage.show();
        Railroad.SETTINGS_HANDLER.loadSettingsFromFile();
        stage.setOnCloseRequest(event -> Railroad.SETTINGS_HANDLER.loadSettingsFromFile());
    }

    public void close() {
        stage.close();
    }
}
