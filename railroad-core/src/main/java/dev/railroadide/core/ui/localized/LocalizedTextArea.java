package dev.railroadide.core.ui.localized;

import javafx.scene.control.TextArea;

/**
 * An extension of the JavaFX TextArea that allows for the TextArea's prompt text to be localised.
 */
public class LocalizedTextArea extends TextArea {
    
    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    /**
     * Sets the key and sets the prompt text to the localized key.
     *
     * @param key The key to be localized.
     */
    public LocalizedTextArea(final String key) {
        super();
        promptTextProperty().bindBidirectional(localizedText);
        setKey(key);
    }

    /**
     * Gets the current key.
     *
     * @return The current key.
     */
    public String getKey() {
        return localizedText.getTranslationKey();
    }

    /**
     * Sets the current key, and sets the prompt text to the localized key.
     * Also adds a listener to the current language property to update the text when the language changes.
     *
     * @param key The key to be localized
     */
    public void setKey(final String key) {
        localizedText.setTranslationKey(key);
    }
}
