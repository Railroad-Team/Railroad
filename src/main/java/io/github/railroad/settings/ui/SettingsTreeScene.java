package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.settings.handler.SettingNode;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.skin.TreeViewSkin;
import javafx.stage.Stage;

public class SettingsTreeScene {
    public SettingsTreeScene() {
        var stage = new Stage();

        stage.setTitle("Settings");
        //TODO instead of these manual registrations, get the list of settings from the settings manager
        //And then work through them and add them. Possibly create a way to parent settings to eachother
        //Possibly by passing in a parentid in the registration of the setting, and then also an id and then L18n key
        //Also apply default values & options here

        //TODO localization

        var root = new TreeItem<Node>(new LocalizedLabel("railroad.home.welcome.settings"));
        var scoobySetting = new SettingNode<>(new CheckBox("Enable scooby-doo"));
        var scoobyTreeItem = new TreeItem<Node>(scoobySetting.getNode());
        var scoobyString = new SettingNode<>(new LocalizedLabel("railroad.ide.welcome.features"));
        scoobyTreeItem.getChildren().add(new TreeItem<>(scoobyString.getNode()));

        root.getChildren().add(scoobyTreeItem);
        var tree = new TreeView<>(root);

        scoobySetting.getNode().setSelected(true);
        scoobySetting.getNode().selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                scoobyString.getNode().setKey("railroad.project.creation.location.warning.onedrive");
            } else {
                scoobyString.getNode().setKey("railroad.project.creation.name");
            }

        });

        var scene = new Scene(tree, 500, 700);

        stage.setScene(scene);
        stage.show();
    }
}
