package io.github.railroad.settings.ui;

import io.github.railroad.Railroad;
import io.github.railroad.localization.ui.LocalizedButton;
import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.ui.defaults.RRHBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.stage.Stage;

public class TreeViewSettings {
    private Stage stage;

    //TODO make it, well, not look like this - fix button positioning, treeview border etc
    public TreeViewSettings() {
        stage = new Stage();

        stage.setTitle("Settings");

        var hbox = new HBox();

        var tree = Railroad.SETTINGS_HANDLER.createCategoryTree();
        var treContent = new ScrollPane(Railroad.SETTINGS_HANDLER.createSettingsSection(null));

        tree.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                var selected = newValue.getValue();
                if (selected != null) {
                    treContent.setContent(Railroad.SETTINGS_HANDLER.createSettingsSection(((LocalizedLabel) selected).getText()));
                }
            }
        });

        hbox.getChildren().addAll(tree, treContent);

        var scene = new Scene(hbox, 600, 700);

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
