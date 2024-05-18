package io.github.railroad.settings.ui.categories.general;

import io.github.railroad.ui.defaults.RRHeader;
import io.github.railroad.ui.defaults.RRSeparator;
import io.github.railroad.ui.defaults.RRTitle;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

public class SettingsGeneralPane extends RRVBox {

    public SettingsGeneralPane() {
        var title = new RRTitle("General");
        title.prefWidthProperty().bind(widthProperty());

        var githubBox = new RRVBox(10);
        githubBox.setAlignment(Pos.CENTER_LEFT);
        var githubTitleLabel = new RRHeader(2,"GitHub Connection:");

        // GitHub connection
        GithubConnectionPane githubConnectionPane = new GithubConnectionPane();
        githubBox.getChildren().addAll(githubTitleLabel, githubConnectionPane);

        getChildren().addAll(
                title,
                new RRSeparator(),
                githubBox
        );

        setSpacing(10);
        setPadding(new Insets(10));
    }
}
