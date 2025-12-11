package dev.railroadide.core.ui.localized;

import javafx.scene.control.Tooltip;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * An extension of the JavaFX Tooltip that allows for the Tooltip's text to be localised.
 */
@Getter
public class LocalizedTooltip extends Tooltip {
    
    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    /**
     * Creates a new LocalizedTooltip and sets the key and args.
     *
     * @param key  The localization key
     * @param args The args to be applied to the localization key
     */
    public LocalizedTooltip(@NotNull String key, @NotNull Object... args) {
        super();
        textProperty().bindBidirectional(localizedText);
        setKey(key, args);
    }

    /**
     * Updates the key and args, and then updates the text of the tooltip.
     * Adds a listener to the current language property to update the text when the language changes.
     *
     * @param key  The localization key
     * @param args The args to be applied to the localized key
     */
    public void setKey(@NotNull String key, @NotNull Object... args) {
        localizedText.setTranslation(key, args);
    }
}
