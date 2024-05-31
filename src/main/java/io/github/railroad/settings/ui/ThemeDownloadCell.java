package io.github.railroad.settings.ui;

import com.google.gson.JsonObject;
import io.github.railroad.ui.defaults.RRStackPane;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.StackPane;

public class ThemeDownloadCell extends ListCell<JsonObject> {
    private final StackPane node = new RRStackPane();
    private final ThemeDownloadNode themeDownloadNode = new ThemeDownloadNode();

    public ThemeDownloadCell() {
        node.getChildren().add(themeDownloadNode);

        var button = new Button("Hello");

        node.getChildren().add(button);
    }

    @Override
    protected void updateItem(JsonObject item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            themeDownloadNode.jsonProperty().set(null);
        } else {
            themeDownloadNode.jsonProperty().set(item);
            setGraphic(node);
        }
    }

    public static class ThemeDownloadNode extends RRVBox {
        private final ObjectProperty<JsonObject> jsonProperty = new SimpleObjectProperty<>();
        private final Label theme;

        public ThemeDownloadNode() {
            getStyleClass().add("project-list-node");

            setSpacing(5);
            setPadding(new Insets(10));
            setAlignment(Pos.CENTER_LEFT);

            var themeLabel = new Label();
            themeLabel.textProperty().bind(jsonProperty.map(e -> e.get("name").toString()));

            this.theme = themeLabel;

            getChildren().addAll(themeLabel);
        }

        public ThemeDownloadNode(JsonObject obj) {
            this();
            this.jsonProperty.set(obj);
        }

        public ObjectProperty<JsonObject> jsonProperty() { return jsonProperty; }
    }
}
