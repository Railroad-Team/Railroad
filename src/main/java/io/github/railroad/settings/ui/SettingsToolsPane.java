package io.github.railroad.settings.ui;

import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

public class SettingsToolsPane extends RRVBox {

    public SettingsToolsPane() {
        var title = new LocalizedLabel("railroad.home.settings.tools");
        title.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        title.prefWidthProperty().bind(widthProperty());
        title.setAlignment(Pos.CENTER);

        setPadding(new Insets(10));
        setSpacing(10);

        getChildren().addAll(title);
    }
}