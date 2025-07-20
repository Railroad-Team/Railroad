package io.github.railroad.core.ui.localized;

import io.github.railroad.core.localization.LocalizationServiceLocator;
import javafx.scene.text.Text;

/**
 * An extension of the JavaFX Text that allows for the Text's text to be localised.
 */
public class LocalizedText extends Text {
    private String currentKey;

    /**
     * Sets the key and then the set the text to the localized key.
     * @param key The key to be localized.
     */
    public LocalizedText(final String key) {
        super();
        setKey(key);
        setText(LocalizationServiceLocator.getInstance().get(key));
    }

    /**
     * Gets the current key used for localization.
     * @return The current localization key.
     */
    public String getKey() {
        return currentKey;
    }

    /**
     * Sets the key and then updates the text of the label.
     * Adds a listener to the current language property to update the text when the language changes.
     * @param key The localization key
     */
    public void setKey(final String key) {
        currentKey = key;
        LocalizationServiceLocator.getInstance().currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(LocalizationServiceLocator.getInstance().get(key)));
        setText(LocalizationServiceLocator.getInstance().get(currentKey));
    }
}