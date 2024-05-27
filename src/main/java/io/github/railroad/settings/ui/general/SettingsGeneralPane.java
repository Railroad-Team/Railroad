package io.github.railroad.settings.ui.general;

import io.github.railroad.ui.localized.LocalizedLabel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;

public class SettingsGeneralPane extends VBox {
    public SettingsGeneralPane() {
        var titleLabel = new LocalizedLabel("railroad.home.settings.general");
        titleLabel.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        titleLabel.prefWidthProperty().bind(widthProperty());
        titleLabel.setAlignment(Pos.CENTER);

        getChildren().addAll(titleLabel);

        setSpacing(10);
        setPadding(new Insets(10));
    }
}
