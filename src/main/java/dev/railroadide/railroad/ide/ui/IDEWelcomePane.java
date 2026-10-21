package dev.railroadide.railroad.ide.ui;

import dev.railroadide.railroad.command.ApplicationCommands;
import dev.railroadide.railroad.command.CommandButtons;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome6.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import javafx.scene.layout.FlowPane;

/**
 * Displays the empty-editor welcome view with feature information and getting-started links.
 */
public class IDEWelcomePane extends ScrollPane {
    /**
     * Creates the empty-editor welcome view.
     */
    public IDEWelcomePane() {
        var content = new VBox();
        content.setAlignment(Pos.TOP_LEFT);
        content.setSpacing(28);
        content.setPadding(new Insets(32));
        content.setMaxWidth(860);
        content.setMinWidth(0);
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

        getStyleClass().addAll("welcome-scroll-pane", "ide-welcome-root");
        setPadding(Insets.EMPTY);
    }

    private VBox createWelcomeCard() {
        var card = new VBox(12);
        card.setAlignment(Pos.TOP_LEFT);
        card.setSpacing(16);

        var welcomeIcon = new FontIcon(FontAwesomeSolid.ROCKET);
        welcomeIcon.setIconSize(20);
        welcomeIcon.getStyleClass().add("welcome-icon");

        var welcomeText = label("railroad.ide.welcome.message");
        welcomeText.getStyleClass().addAll("ide-welcome-text", "welcome-title");

        var descriptionText = label("railroad.ide.welcome.message.description");
        descriptionText.getStyleClass().addAll("ide-description-text", "welcome-description");

        card.getChildren().addAll(welcomeIcon, welcomeText, descriptionText);
        return card;
    }

    private VBox createFeaturesCard() {
        var card = new VBox(12);
        card.setSpacing(12);

        var featuresHeader = new HBox(12);
        featuresHeader.setAlignment(Pos.CENTER_LEFT);

        var featuresIcon = new FontIcon(FontAwesomeSolid.STAR);
        featuresIcon.setIconSize(16);
        featuresIcon.getStyleClass().add("features-icon");

        var featuresTitle = label("railroad.ide.welcome.features");
        featuresTitle.getStyleClass().addAll("ide-features-text", "section-title");

        featuresHeader.getChildren().addAll(featuresIcon, featuresTitle);

        var featuresGrid = new VBox(8);
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

        card.getChildren().addAll(featuresHeader, featuresGrid);
        return card;
    }

    private Node createFeatureItem(FeatureItem feature) {
        var container = new HBox(12);
        container.setAlignment(Pos.CENTER_LEFT);
        container.getStyleClass().add("feature-item");

        var icon = new FontIcon(feature.icon());
        icon.setIconSize(16);
        icon.getStyleClass().add("feature-icon");

        var text = label(feature.localizationKey());
        text.getStyleClass().add("feature-text");

        container.getChildren().addAll(icon, text);
        return container;
    }

    private VBox createGettingStartedCard() {
        var card = new VBox(12);
        card.setSpacing(12);

        var header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        var headerIcon = new FontIcon(FontAwesomeSolid.PLAY_CIRCLE);
        headerIcon.setIconSize(16);
        headerIcon.getStyleClass().add("getting-started-icon");

        var headerTitle = label("railroad.ide.welcome.getting_started");
        headerTitle.getStyleClass().addAll("ide-getting-started-text", "section-title");

        header.getChildren().addAll(headerIcon, headerTitle);

        var descriptionText = label("railroad.ide.welcome.getting_started.description");
        descriptionText.getStyleClass().addAll("ide-getting-started-description", "section-description");

        var buttonsContainer = new FlowPane(8, 8);
        buttonsContainer.setAlignment(Pos.CENTER_LEFT);

        var wikiButton = new RRButton("railroad.ide.welcome.getting_started.wiki", FontAwesomeSolid.BOOK);
        wikiButton.setVariant(ButtonVariant.PRIMARY);
        CommandButtons.bind(wikiButton, ApplicationCommands.OPEN_LINK,
            () -> CommandContext.withArgument(null, this, "https://railroadide.dev"));

        var tutorialsButton = new RRButton("railroad.ide.welcome.getting_started.tutorials",
            FontAwesomeSolid.GRADUATION_CAP);
        tutorialsButton.setVariant(ButtonVariant.SECONDARY);
        CommandButtons.bind(tutorialsButton, ApplicationCommands.OPEN_LINK,
            () -> CommandContext.withArgument(null, this, "https://railroadide.dev/tutorials"));

        var discordButton = new RRButton("", FontAwesomeBrands.DISCORD);
        discordButton.setText("Discord");
        discordButton.setVariant(ButtonVariant.GHOST);
        CommandButtons.bind(discordButton, ApplicationCommands.OPEN_LINK,
            () -> CommandContext.withArgument(null, this, "https://discord.turtywurty.dev/"));

        buttonsContainer.getChildren().addAll(wikiButton, tutorialsButton, discordButton);

        card.getChildren().addAll(header, descriptionText, buttonsContainer);
        return card;
    }

    private static LocalizedLabel label(String key) {
        var label = new LocalizedLabel(key);
        label.setWrapText(true);
        label.setMinWidth(0);
        return label;
    }

    private record FeatureItem(FontAwesomeSolid icon, String localizationKey) {
    }
}
