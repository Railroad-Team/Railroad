package dev.railroadide.railroad.ide.ui;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRCard;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.core.utility.DesktopUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class IDEWelcomePane extends ScrollPane {
    public IDEWelcomePane() {
        var content = new RRVBox();
        content.setAlignment(Pos.CENTER);
        content.setSpacing(24);
        content.setPadding(new Insets(32));
        content.setMaxWidth(Double.MAX_VALUE);
        content.setPrefWidth(800);

        var welcomeCard = createWelcomeCard();
        content.getChildren().add(welcomeCard);

        var featuresCard = createFeaturesCard();
        content.getChildren().add(featuresCard);

        var gettingStartedCard = createGettingStartedCard();
        content.getChildren().add(gettingStartedCard);

        setContent(content);
        setFitToWidth(true);
        setFitToHeight(false);
        setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        setPannable(true);

        getStyleClass().add("welcome-scroll-pane");
        setPadding(Insets.EMPTY);
    }

    private RRCard createWelcomeCard() {
        var card = new RRCard(16, new Insets(32));
        card.setAlignment(Pos.CENTER);
        card.setSpacing(16);

        var welcomeIcon = new FontIcon(FontAwesomeSolid.ROCKET);
        welcomeIcon.setIconSize(48);
        welcomeIcon.getStyleClass().add("welcome-icon");

        var welcomeText = new LocalizedText("railroad.ide.welcome.message");
        welcomeText.getStyleClass().addAll("ide-welcome-text", "welcome-title");

        var descriptionText = new LocalizedText("railroad.ide.welcome.message.description");
        descriptionText.getStyleClass().addAll("ide-description-text", "welcome-description");

        card.addContent(welcomeIcon, welcomeText, descriptionText);
        return card;
    }

    private RRCard createFeaturesCard() {
        var card = new RRCard(16, new Insets(24));
        card.setSpacing(20);

        var featuresHeader = new HBox(12);
        featuresHeader.setAlignment(Pos.CENTER_LEFT);

        var featuresIcon = new FontIcon(FontAwesomeSolid.STAR);
        featuresIcon.setIconSize(24);
        featuresIcon.getStyleClass().add("features-icon");

        var featuresTitle = new LocalizedText("railroad.ide.welcome.features");
        featuresTitle.getStyleClass().addAll("ide-features-text", "section-title");

        featuresHeader.getChildren().addAll(featuresIcon, featuresTitle);

        var featuresGrid = new VBox(16);
        featuresGrid.getStyleClass().add("features-grid");

        var features = new FeatureItem[]{
            new FeatureItem(FontAwesomeSolid.CUBE, "railroad.ide.welcome.feature.model_viewer_editor"),
            new FeatureItem(FontAwesomeSolid.BUILDING, "railroad.ide.welcome.feature.structure_viewer_editor"),
            new FeatureItem(FontAwesomeSolid.IMAGE, "railroad.ide.welcome.feature.texture_viewer_editor"),
            new FeatureItem(FontAwesomeSolid.VOLUME_UP, "railroad.ide.welcome.feature.sound_visualizer"),
            new FeatureItem(FontAwesomeSolid.CODE, "railroad.ide.welcome.feature.mixin_support")
        };

        for (var feature : features) {
            featuresGrid.getChildren().add(createFeatureItem(feature));
        }

        card.addContent(featuresHeader, featuresGrid);
        return card;
    }

    private Node createFeatureItem(FeatureItem feature) {
        var container = new HBox(12);
        container.setAlignment(Pos.CENTER_LEFT);
        container.getStyleClass().add("feature-item");

        var icon = new FontIcon(feature.icon());
        icon.setIconSize(20);
        icon.getStyleClass().add("feature-icon");

        var text = new LocalizedText(feature.localizationKey());
        text.getStyleClass().add("feature-text");

        container.getChildren().addAll(icon, text);
        return container;
    }

    private RRCard createGettingStartedCard() {
        var card = new RRCard(16, new Insets(24));
        card.setSpacing(20);

        var header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        var headerIcon = new FontIcon(FontAwesomeSolid.PLAY_CIRCLE);
        headerIcon.setIconSize(24);
        headerIcon.getStyleClass().add("getting-started-icon");

        var headerTitle = new LocalizedText("railroad.ide.welcome.getting_started");
        headerTitle.getStyleClass().addAll("ide-getting-started-text", "section-title");

        header.getChildren().addAll(headerIcon, headerTitle);

        var descriptionText = new LocalizedText("railroad.ide.welcome.getting_started.description");
        descriptionText.getStyleClass().addAll("ide-getting-started-description", "section-description");

        var buttonsContainer = new RRHBox();
        buttonsContainer.setAlignment(Pos.CENTER);
        buttonsContainer.setSpacing(12);

        var wikiButton = new RRButton("railroad.ide.welcome.getting_started.wiki", FontAwesomeSolid.BOOK);
        wikiButton.setVariant(RRButton.ButtonVariant.PRIMARY);
        wikiButton.setOnAction(event -> {
            event.consume();
            DesktopUtils.openUrl("https://railroadide.dev");
        });

        var tutorialsButton = new RRButton("railroad.ide.welcome.getting_started.tutorials", FontAwesomeSolid.GRADUATION_CAP);
        tutorialsButton.setVariant(RRButton.ButtonVariant.SECONDARY);
        tutorialsButton.setOnAction(event -> {
            event.consume();
            DesktopUtils.openUrl("https://railroadide.dev/tutorials");
        });

        var discordButton = new RRButton("", FontAwesomeBrands.DISCORD);
        discordButton.setText("Discord");
        discordButton.setVariant(RRButton.ButtonVariant.GHOST);
        discordButton.setOnAction(event -> {
            event.consume();
            DesktopUtils.openUrl("https://discord.turtywurty.dev/");
        });

        buttonsContainer.getChildren().addAll(wikiButton, tutorialsButton, discordButton);

        card.addContent(header, descriptionText, buttonsContainer);
        return card;
    }

    private record FeatureItem(FontAwesomeSolid icon, String localizationKey) {
    }
}
