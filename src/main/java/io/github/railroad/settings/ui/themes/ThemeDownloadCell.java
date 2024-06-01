package io.github.railroad.settings.ui.themes;

import com.google.gson.JsonObject;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRStackPane;
import io.github.railroad.ui.localized.LocalizedButton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.StackPane;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import org.gradle.internal.impldep.org.apache.commons.lang.WordUtils;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.MalformedURLException;
import java.net.URL;

public class ThemeDownloadCell extends ListCell<JsonObject> {
    private final StackPane node = new RRStackPane();
    private final ThemeDownloadNode themeDownloadNode = new ThemeDownloadNode();
    private final LocalizedButton downloadButton = new LocalizedButton("railroad.home.settings.appearance.download");
    private final Button previewButton = new Button();

    public ThemeDownloadCell() {
        var box = new RRHBox();
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setSpacing(10);

        setPadding(new Insets(5));
        setMaxWidth(Screen.getPrimary().getBounds().getMaxX());

        previewButton.setAlignment(Pos.BASELINE_CENTER);
        previewButton.setGraphic(new FontIcon(FontAwesomeSolid.EYE));
        previewButton.setBackground(null);


        downloadButton.setPrefWidth(87.2);
        downloadButton.setAlignment(Pos.CENTER_RIGHT);
        downloadButton.setTextAlignment(TextAlignment.JUSTIFY);

        setPrefHeight(75);

        themeDownloadNode.setAlignment(Pos.CENTER_LEFT);

        downloadButton.setOnAction(action -> {
            try {
                boolean res = ThemeDownloadManager.downloadTheme(new URL(themeDownloadNode.jsonProperty().get().get("download_url").getAsString()));
                this.downloadButton.setKey(res ? "railroad.home.settings.appearance.installed" : "railroad.home.settings.appearance.download");
                this.downloadButton.setDisable(res);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        });

        themeDownloadNode.jsonProperty().addListener((observable, oldValue, newValue) -> {
            boolean isDownloaded = newValue != null && ThemeDownloadManager.isDownloaded(newValue.get("name").toString().replace("\"", ""));
            this.downloadButton.setKey(isDownloaded ? "railroad.home.settings.appearance.installed" : "railroad.home.settings.appearance.download");
            this.downloadButton.setDisable(isDownloaded);
        });


        box.getChildren().addAll(downloadButton, previewButton);

        node.getChildren().addAll(themeDownloadNode, box);
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

    public static class ThemeDownloadNode extends RRHBox {
        private final ObjectProperty<JsonObject> jsonProperty = new SimpleObjectProperty<>();

        public ThemeDownloadNode() {
            getStyleClass().add("project-list-node");
            setSpacing(5);
            setPadding(new Insets(10));

            var themeName = jsonProperty.map(e ->
                    WordUtils.capitalize(
                            e.get("name").toString()
                                    .replace("\"", "")
                                    .replace(".css", "")
                                    .replace("-", " ")
                    ));

            var themeLabel = new Label();
            themeLabel.textProperty().bind(themeName);
            themeLabel.setAlignment(Pos.BASELINE_LEFT);
            themeLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16;");

            getChildren().addAll(themeLabel);
        }

        public ThemeDownloadNode(JsonObject obj) {
            this();
            this.jsonProperty.set(obj);
        }

        public ObjectProperty<JsonObject> jsonProperty() { return jsonProperty; }
    }
}
