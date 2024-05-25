package io.github.railroad.settings.ui;

import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;

public class SettingsProjectsPane extends RRVBox {

    public SettingsProjectsPane() {
        var title = new Label("Projects");
        title.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        title.prefWidthProperty().bind(widthProperty());
        title.setAlignment(Pos.CENTER);

        setPadding(new Insets(10));
        setSpacing(10);

        getChildren().addAll(title);
    }
}
