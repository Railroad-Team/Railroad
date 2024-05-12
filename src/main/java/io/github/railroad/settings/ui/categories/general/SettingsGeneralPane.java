package io.github.railroad.settings.ui.categories.general;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

        getChildren().addAll(titleLabel, githubBox);

        setSpacing(10);
        setPadding(new Insets(10));
    }
}
