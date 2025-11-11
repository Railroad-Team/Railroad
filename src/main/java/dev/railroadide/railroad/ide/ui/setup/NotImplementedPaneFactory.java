package dev.railroadide.railroad.ide.ui.setup;

import dev.railroadide.core.ui.RRCard;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.text.TextAlignment;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Simple factory for the placeholder pane used when a feature has not been implemented yet.
 */
public final class NotImplementedPaneFactory {
    private NotImplementedPaneFactory() {
    }

    public static Node create() {
        var card = new RRCard(16);
        card.setPadding(new Insets(32, 32, 32, 32));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setAlignment(Pos.CENTER);

        var icon = new FontIcon(FontAwesomeSolid.TOOLS);
        icon.setIconSize(48);
        icon.getStyleClass().add("not-implemented-icon");

        var title = new LocalizedLabel("not_implemented.title");
        title.getStyleClass().add("not-implemented-title");
        title.setAlignment(Pos.CENTER);
        title.setTextAlignment(TextAlignment.CENTER);
        title.setWrapText(true);

        var subtitle = new LocalizedLabel("not_implemented.subtitle");
        subtitle.getStyleClass().add("not-implemented-subtitle");
        subtitle.setWrapText(true);
        subtitle.setAlignment(Pos.CENTER);
        subtitle.setTextAlignment(TextAlignment.CENTER);

        card.addContent(icon, title, subtitle);
        return card;
    }
}
