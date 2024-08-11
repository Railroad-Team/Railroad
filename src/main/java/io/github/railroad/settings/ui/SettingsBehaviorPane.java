package io.github.railroad.settings.ui;

import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

public class SettingsBehaviorPane extends RRVBox {
    public SettingsBehaviorPane() {
        var title = new LocalizedLabel("railroad.home.settings.behavior");
        title.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        title.prefWidthProperty().bind(widthProperty());
        title.setAlignment(Pos.CENTER);

        setPadding(new Insets(10));
        setSpacing(10);

        getChildren().add(title);
    }
}