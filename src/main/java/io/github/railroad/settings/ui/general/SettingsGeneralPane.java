package io.github.railroad.settings.ui.general;

import io.github.railroad.ui.localized.LocalizedLabel;
import io.github.railroad.utility.localization.L18n;
import io.github.railroad.utility.localization.Languages;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.VBox;

import java.util.Arrays;

public class SettingsGeneralPane extends VBox {
    // GitHub connection
    private final GithubConnectionPane githubConnectionPane = new GithubConnectionPane();

    public SettingsGeneralPane() {
        var titleLabel = new LocalizedLabel("railroad.home.settings.general");
        titleLabel.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        titleLabel.prefWidthProperty().bind(widthProperty());
        titleLabel.setAlignment(Pos.CENTER);

        var githubBox = new VBox(10);
        githubBox.setAlignment(Pos.CENTER_LEFT);
        var githubTitleLabel = new LocalizedLabel("railroad.home.settings.general.github");
        githubTitleLabel.setStyle("-fx-font-weight: bold;");
        githubBox.getChildren().addAll(githubTitleLabel, githubConnectionPane);

        var langBox = new VBox(10);
        langBox.setAlignment(Pos.CENTER_LEFT);
        var langTitleLabel = new LocalizedLabel("railroad.home.settings.general.language");
        langTitleLabel.setStyle("-fx-font-weight: bold;");

        var langComboBox = new ComboBox<>();
        langComboBox.getItems().addAll(Arrays.stream(Languages.values()).map(Languages::getName).toList());

        langComboBox.setValue(L18n.getCurrentLanguage().getName());
        langComboBox.setOnAction(e -> {
            L18n.setLanguage(Languages.fromName(langComboBox.getValue().toString()));
        });

        langBox.getChildren().addAll(langTitleLabel, langComboBox);

        getChildren().addAll(titleLabel, githubBox, langBox);

        setSpacing(10);
        setPadding(new Insets(10));
    }
}
