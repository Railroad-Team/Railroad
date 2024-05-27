package io.github.railroad.settings.ui.plugin;

import io.github.railroad.plugin.Plugin;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.defaults.RRStackPane;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;

public class PluginListCell extends ListCell<Plugin> {
    private final RRStackPane node = new RRStackPane();
    private final PluginListCell.PluginListNode pluginListNode = new PluginListCell.PluginListNode();

    public PluginListCell(ScrollPane pane) {
        var ellipseButton = new Button("...");
        ellipseButton.setBackground(null);
        RRStackPane.setAlignment(ellipseButton, Pos.TOP_RIGHT);

        var dropdown = new ContextMenu();
        var settingsItem = new MenuItem("Settings");
        var removeItem = new MenuItem("Remove");

        settingsItem.setOnAction(e -> {
            Plugin plugin = pluginListNode.pluginProperty().get();
            if (plugin != null) {
                RRVBox setting_pane = plugin.showSettings();
                if (setting_pane != null) {
                    pane.setContent(setting_pane);
                    RRVBox.setVgrow(setting_pane, Priority.ALWAYS);
                    RRHBox.setHgrow(setting_pane, Priority.ALWAYS);
                }
            }
        });

        dropdown.getItems().addAll(settingsItem, removeItem);

        ellipseButton.setOnMouseClicked(e -> {
            dropdown.show(ellipseButton, e.getScreenX(), e.getScreenY());
        });

        getStyleClass().add("project-list-cell");
        node.getChildren().add(pluginListNode);
        node.getChildren().add(ellipseButton);
    }

    @Override
    protected void updateItem(Plugin plugin, boolean empty) {
        super.updateItem(plugin, empty);

        if (empty || plugin == null) {
            setText(null);
            setGraphic(null);
            pluginListNode.pluginProperty().set(null);
        } else {
            pluginListNode.pluginProperty().set(plugin);
            setGraphic(node);
        }
    }

    public static class PluginListNode extends RRVBox {
        private final ObjectProperty<Plugin> plugin = new SimpleObjectProperty<>();

        public PluginListNode() {
            getStyleClass().add("project-list-node");

            setSpacing(5);
            setPadding(new Insets(10));
            setAlignment(Pos.CENTER_LEFT);

            var nameLabel = new Label();
            nameLabel.textProperty().bind(plugin.map(Plugin::getPluiginName));
            nameLabel.setStyle("-fx-font-size: 16px;");

            var icon = new ImageView();
            icon.setFitWidth(32);
            icon.setFitHeight(32);
            icon.setPreserveRatio(true);

            getChildren().addAll(icon, nameLabel);
        }

        public PluginListNode(Plugin plugin) {
            this();
            this.plugin.set(plugin);
        }

        public ObjectProperty<Plugin> pluginProperty() {
            return plugin;
        }
    }
}
