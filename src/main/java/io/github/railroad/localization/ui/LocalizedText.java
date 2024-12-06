package io.github.railroad.localization.ui;

import io.github.railroad.localization.L18n;
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
        setText(L18n.localize(key));
    }

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
        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(L18n.localize(key)));
        setText(L18n.localize(currentKey));
    }
}