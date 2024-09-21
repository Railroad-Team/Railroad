package io.github.railroad.settings.ui.themes;

import io.github.railroad.localization.ui.LocalizedButton;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRStackPane;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Background;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import org.gradle.internal.impldep.org.apache.commons.lang.WordUtils;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class ThemeDownloadCell extends ListCell<Theme> {
    private final RRStackPane node = new RRStackPane();
    private final ThemeDownloadNode themeDownloadNode = new ThemeDownloadNode();
    private final LocalizedButton downloadButton = new LocalizedButton("railroad.home.settings.appearance.download");
    private final Button previewButton = new Button();

    public ThemeDownloadCell() {
        setPrefHeight(75);

        var box = new RRHBox();
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setSpacing(10);

        setPadding(new Insets(5));
        setMaxWidth(Screen.getPrimary().getBounds().getMaxX());

        previewButton.setAlignment(Pos.BASELINE_CENTER);
        previewButton.setGraphic(new FontIcon(FontAwesomeSolid.EYE));
        previewButton.setBackground(Background.EMPTY);

        downloadButton.setPrefWidth(87.2);
        downloadButton.setAlignment(Pos.CENTER);
        downloadButton.setTextAlignment(TextAlignment.JUSTIFY);

        themeDownloadNode.setAlignment(Pos.CENTER_LEFT);

        downloadButton.setOnAction(action -> {
            boolean resolved = ThemeDownloadManager.downloadTheme(themeDownloadNode.themePropertyProperty().get());
            this.downloadButton.setKey(resolved ? "railroad.home.settings.appearance.installed" : "railroad.home.settings.appearance.download");
            this.downloadButton.setDisable(resolved);
        });

        themeDownloadNode.themePropertyProperty().addListener((observable, oldValue, newValue) -> {
            boolean isDownloaded = newValue != null && ThemeDownloadManager.isDownloaded(newValue);
            this.downloadButton.setKey(isDownloaded ? "railroad.home.settings.appearance.installed" : "railroad.home.settings.appearance.download");
            this.downloadButton.setDisable(isDownloaded);
        });

        previewButton.setOnAction(action -> {
            new ThemeExamplePane(themeDownloadNode.themePropertyProperty().map(Theme::getName).getValue());
        });

        box.getChildren().addAll(downloadButton, previewButton);
        node.getChildren().addAll(themeDownloadNode, box);
    }

    @Override
    protected void updateItem(Theme item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            themeDownloadNode.themePropertyProperty().set(null);
        } else {
            themeDownloadNode.themePropertyProperty().set(item);
            setGraphic(node);
        }
    }

    public static class ThemeDownloadNode extends RRHBox {
        private final ObjectProperty<Theme> themeProperty = new SimpleObjectProperty<>();

        public ThemeDownloadNode() {
            getStyleClass().add("project-list-node");
            setSpacing(5);
            setPadding(new Insets(10));

            ObservableValue<String> themeName = themeProperty.map(theme ->
                    WordUtils.capitalize(
                            theme.getName()
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

        public ThemeDownloadNode(Theme obj) {
            this();
            this.themeProperty.set(obj);
        }

        public ObjectProperty<Theme> themePropertyProperty() {
            return themeProperty;
        }
    }
}
