package io.github.railroad.settings.ui;

import com.google.gson.JsonObject;
import io.github.railroad.project.data.Project;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedButton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.StackPane;

public class ThemeDownloadCell extends ListCell<Object> {
    private final StackPane node = new StackPane();
    private final ThemeDownloadNode themeDownloadNode = new ThemeDownloadNode();

    public ThemeDownloadCell(final String cssName) {
        getStyleClass().add("theme-download-cell");
        node.getChildren().add(themeDownloadNode);

        setPadding(new Insets(10));
        setAlignment(Pos.BASELINE_LEFT);

        var name = new Label(cssName);
        var downloadButton = new LocalizedButton("railroad.home.settings.appearance.download");

        node.getChildren().addAll(downloadButton);
    }

    public static class ThemeDownloadNode extends RRVBox {
        private final ObjectProperty<JsonObject> object = new SimpleObjectProperty<>();
        Label label = null;

        public ThemeDownloadNode() {
            setPadding(new Insets(5));
            label = new Label("test");

            var tbox = new RRHBox(5);
            tbox.getChildren().add(label);
            getChildren().add(tbox);
        }

        public ThemeDownloadNode(JsonObject obj) {
            this();
            this.object.set(obj);
        }

        public ObjectProperty<JsonObject> projectProperty() {
            return object;
        }
    }
}
