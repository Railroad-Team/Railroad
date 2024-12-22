package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.localization.ui.LocalizedButton;
import io.github.railroad.ui.defaults.RRHBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;

public class TreeViewSettings {
    private Stage stage;

    //TODO make it, well, not look like this - fix button positioning, treeview border etc
    public TreeViewSettings() {
        stage = new Stage();

        stage.setTitle("Settings");

        var vbox = new VBox();
        vbox.setAlignment(Pos.TOP_CENTER);
        vbox.setMinHeight(700);

        var tree = Railroad.SETTINGS_HANDLER.createTree();
        tree.setPrefHeight(500);

        var subBox = new VBox();
        subBox.setPadding(new Insets(25));

        var buttons = new RRHBox();
        buttons.setAlignment(Pos.BOTTOM_RIGHT);
        buttons.setPadding(new Insets(25));

        subBox.getChildren().add(buttons);

        var button = new LocalizedButton("railroad.generic.apply");
        button.setAlignment(Pos.BOTTOM_RIGHT);
        button.setStyle("-fx-background-color: -color-accent-6");
        button.setOnAction(event -> Railroad.SETTINGS_HANDLER.saveSettingsFile());

        buttons.getChildren().add(button);
        buttons.setAlignment(Pos.BOTTOM_RIGHT);

        vbox.getChildren().addAll(tree,subBox);
        var scene = new Scene(vbox, 600, 700);

        Railroad.handleStyles(scene);

        stage.setScene(scene);
        stage.show();
        Railroad.SETTINGS_HANDLER.loadSettingsFromFile();

        //Reset to settings saved when closed without applying
        stage.setOnCloseRequest(event -> {
            Railroad.LOGGER.info("Settings window closed - Reloading settings");
            Railroad.SETTINGS_HANDLER.loadSettingsFromFile();
            Railroad.SETTINGS_HANDLER.getSettings().reloadSettings();
        });
    }

    public void close() {
        stage.close();
    }
}
