package io.github.railroad.settings.ui.general;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class SettingsGeneralPane extends VBox {
    public SettingsGeneralPane() {
        var titleLabel = new Label("General");
        titleLabel.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        titleLabel.prefWidthProperty().bind(widthProperty());
        titleLabel.setAlignment(Pos.CENTER);

        getChildren().addAll(titleLabel);

        setSpacing(10);
        setPadding(new Insets(10));
    }
}
