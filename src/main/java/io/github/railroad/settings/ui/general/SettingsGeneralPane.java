package io.github.railroad.settings.ui.general;

import io.github.railroad.utility.ConfigHandler;
import io.github.railroad.utility.LocalizationHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class SettingsGeneralPane extends VBox {
    // GitHub connection
    private final GithubConnectionPane githubConnectionPane = new GithubConnectionPane();

    public SettingsGeneralPane() {
        var titleLabel = new Label("General");
        titleLabel.setStyle("-fx-font-size: 20pt; -fx-font-weight: bold;");
        titleLabel.prefWidthProperty().bind(widthProperty());
        titleLabel.setAlignment(Pos.CENTER);

        var githubBox = new VBox(10);
        githubBox.setAlignment(Pos.CENTER_LEFT);
        var githubTitleLabel = new Label("GitHub Connection:");
        githubTitleLabel.setStyle("-fx-font-weight: bold;");
        githubBox.getChildren().addAll(githubTitleLabel, githubConnectionPane);

        var langBox = new VBox(10);
        langBox.setAlignment(Pos.CENTER_LEFT);
        var langTitleLabel = new Label("Language:");
        langTitleLabel.setStyle("-fx-font-weight: bold;");

        var langComboBox = new ComboBox<>();
        langComboBox.getItems().addAll("English", "Español", "Français", "Deutsch");
        langComboBox.setValue(LocalizationHandler.convertLanguage(LocalizationHandler.getCurrentLanguage(), false));
        langComboBox.setOnAction(e -> {
            LocalizationHandler.setLanguage(langComboBox.getValue().toString().toLowerCase());
        });

        langBox.getChildren().addAll(langTitleLabel, langComboBox);

        getChildren().addAll(titleLabel, githubBox, langBox);

        setSpacing(10);
        setPadding(new Insets(10));
    }
}
