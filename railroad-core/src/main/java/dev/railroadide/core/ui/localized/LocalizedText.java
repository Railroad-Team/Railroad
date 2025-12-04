package dev.railroadide.core.ui.localized;

import javafx.scene.text.Text;

/**
 * An extension of the JavaFX Text that allows for the Text's text to be localised.
 */
public class LocalizedText extends Text {
    
    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    /**
     * Sets the key and then the set the text to the localized key.
     *
     * @param key The key to be localized.
     */
    public LocalizedText(final String key) {
        super();
        textProperty().bindBidirectional(localizedText);
        setKey(key);
    }

    /**
     * Gets the current key used for localization.
     *
     * @return The current localization key.
     */
    public String getKey() {
        return localizedText.getTranslationKey();
    }

    /**
     * Sets the key and then updates the text of the label.
     * Adds a listener to the current language property to update the text when the language changes.
     *
     * @param key The localization key
     */
    public void setKey(final String key) {
        localizedText.setTranslationKey(key);
    }
}
