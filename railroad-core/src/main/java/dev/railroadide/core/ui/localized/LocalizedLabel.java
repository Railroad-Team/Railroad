package dev.railroadide.core.ui.localized;

import javafx.scene.control.Label;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * An extension of the JavaFX Label that allows for the Label's text to be localised.
 */
@Getter
public class LocalizedLabel extends Label {
    
    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);
    /**
     * Creates a new LocalizedLabel and sets the key and args
     *
     * @param key  The localization key
     * @param args The args to be applied to the localization key
     */
    public LocalizedLabel(@NotNull String key, @NotNull Object... args) {
        super();

        textProperty().bindBidirectional(localizedText);
        setKey(key, args);
    }

    public String getKey() {
        return localizedText.getTranslationKey();
    }

    /**
     * Updates the key and args, and then updates the text of the label.
     * Adds a listener to the current language property to update the text when the language changes.
     *
     * @param key  The localization key
     * @param args The args to be applied to the localized key
     */
    public void setKey(@NotNull String key, @NotNull Object... args) {
        localizedText.setTranslation(key, args);
    }
}
