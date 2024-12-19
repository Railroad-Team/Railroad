package io.github.railroad.settings.ui.general;

import io.github.railroad.localization.L18n;
import io.github.railroad.localization.Language;
import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;

import java.util.Arrays;

public class SettingsGeneralPane extends RRVBox {
    public SettingsGeneralPane() {
        var titleLabel = new LocalizedLabel("railroad.home.settings.general");
        titleLabel.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        titleLabel.prefWidthProperty().bind(widthProperty());
        titleLabel.setAlignment(Pos.CENTER);

        var langBox = new RRVBox(10);
        langBox.setAlignment(Pos.CENTER_LEFT);
        var langTitleLabel = new LocalizedLabel("railroad.home.settings.general.language");
        langTitleLabel.setStyle("-fx-font-weight: bold;");

        var langComboBox = new ComboBox<>();
        langComboBox.getItems().addAll(Arrays.stream(Language.values()).map(Language::getName).toList());

        langComboBox.setValue(L18n.getCurrentLanguage().getName());
        langComboBox.setOnAction(e -> {
            //L18n.setLanguage(Language.fromName(langComboBox.getValue().toString()));
        });

        langBox.getChildren().addAll(langTitleLabel, langComboBox);

        getChildren().addAll(titleLabel, langBox);

        setSpacing(10);
        setPadding(new Insets(10));
    }
}
