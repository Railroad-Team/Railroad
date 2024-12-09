package io.github.railroad.settings.ui;

import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.localization.ui.LocalizedTextField;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;

public class SettingsTreeScene {
    private TreeItem<Node> root;

    public SettingsTreeScene() {
        var stage = new Stage();

        stage.setTitle("Settings");

        var tree = new TreeView<>();

        var scene = new Scene(tree, 500, 700);

        stage.setScene(scene);
        stage.show();
    }
    //Tree example
    //Tree - TreeView<Node>(root)
    //Root - TreeItem<Node>
    // - Scooby-Doo TreeItem<Node>
    //  - Scooby-Doo Checkbox - SettingNode<Checkbox>
    //      - Scooby-Doo String - SettingNode<Label>
    public void findSetting(String settingId) {
        //TODO No clue.
    }
}
