package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.localization.ui.LocalizedButton;
import io.github.railroad.localization.ui.LocalizedText;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import org.kordamp.ikonli.fontawesome5.FontAwesomeBrands;
import org.kordamp.ikonli.javafx.FontIcon;

public class IDEWelcomePane extends RRVBox {
    public IDEWelcomePane() {
        setAlignment(Pos.CENTER);
        setSpacing(10);

        var welcomeText = new LocalizedText("railroad.ide.welcome.message");
        welcomeText.setStyle("-fx-font-weight: bold; -fx-font-size: 30px;");
        getChildren().add(welcomeText);

        var descriptionText = new LocalizedText("railroad.ide.welcome.message.description");
        descriptionText.setStyle("-fx-font-size: 25px;");
        getChildren().add(descriptionText);

        var featuresText = new LocalizedText("railroad.ide.welcome.features");
        featuresText.setStyle("-fx-font-weight: bold; -fx-font-size: 20px;");
        getChildren().add(featuresText);

        var features = new RRVBox();
        features.setAlignment(Pos.CENTER);
        features.setSpacing(10);
        // railroad is an ide for minecraft modding
        features.getChildren().add(new LocalizedText("railroad.ide.welcome.feature.model_viewer_editor"));
        features.getChildren().add(new LocalizedText("railroad.ide.welcome.feature.structure_viewer_editor"));
        features.getChildren().add(new LocalizedText("railroad.ide.welcome.feature.texture_viewer_editor"));
        features.getChildren().add(new LocalizedText("railroad.ide.welcome.feature.sound_visualizer"));
        features.getChildren().add(new LocalizedText("railroad.ide.welcome.feature.mixin_support"));
        getChildren().add(features);

        var gettingStartedText = new LocalizedText("railroad.ide.welcome.getting_started");
        gettingStartedText.setStyle("-fx-font-weight: bold; -fx-font-size: 20px;");
        getChildren().add(gettingStartedText);

        var gettingStartedDescriptionText = new LocalizedText("railroad.ide.welcome.getting_started.description");
        gettingStartedDescriptionText.setStyle("-fx-font-size: 15px;");
        getChildren().add(gettingStartedDescriptionText);

        var wikiButton = new LocalizedButton("railroad.ide.welcome.getting_started.wiki");
        wikiButton.setOnAction(e -> {
            e.consume();

            Railroad.openUrl("https://railroad.turtywurty.dev/wiki");
        });

        var tutorialsButton = new LocalizedButton("railroad.ide.welcome.getting_started.tutorials");
        tutorialsButton.setOnAction(e -> {
            e.consume();

            Railroad.openUrl("https://tutorials.turtywurty.dev/");
        });

        var discordIcon = new FontIcon(FontAwesomeBrands.DISCORD);
        var discordButton = new Button("Discord", discordIcon);
        discordButton.setOnAction(e -> {
            e.consume();

            Railroad.openUrl("https://discord.turtywurty.dev/");
        });

        var buttons = new RRHBox();
        buttons.setAlignment(Pos.CENTER);
        buttons.setSpacing(10);
        buttons.getChildren().addAll(wikiButton, tutorialsButton, discordButton);
        getChildren().add(buttons);
    }
}
