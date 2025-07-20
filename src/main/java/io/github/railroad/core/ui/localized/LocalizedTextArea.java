package io.github.railroad.core.ui.localized;

import io.github.railroad.core.localization.LocalizationServiceLocator;
import javafx.scene.control.TextArea;

/**
 * An extension of the JavaFX TextArea that allows for the TextArea's prompt text to be localised.
 */
public class LocalizedTextArea extends TextArea {
    private String currentKey;

    /**
     * Sets the key and sets the prompt text to the localized key.
     *
     * @param key The key to be localized.
     */
    public LocalizedTextArea(final String key) {
        super();
        if (key != null) {
            setKey(key);
            setPromptText(LocalizationServiceLocator.getInstance().get(key));
        }
    }

    /**
     * Gets the current key.
     *
     * @return The current key.
     */
    public String getKey() {
        return currentKey;
    }

    /**
     * Sets the current key, and sets the prompt text to the localized key.
     * Also adds a listener to the current language property to update the text when the language changes.
     *
     * @param key The key to be localized
     */
    public void setKey(final String key) {
        currentKey = key;
        LocalizationServiceLocator.getInstance().currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setPromptText(LocalizationServiceLocator.getInstance().get(key)));
        setPromptText(LocalizationServiceLocator.getInstance().get(currentKey));
    }
}
