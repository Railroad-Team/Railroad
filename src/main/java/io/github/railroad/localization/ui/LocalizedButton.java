package io.github.railroad.localization.ui;

import io.github.railroad.localization.L18n;
import javafx.scene.control.Button;

/**
 * An extension of the JavaFX Button that allows for the button's text to be a localized string.
 */
public class LocalizedButton extends Button {
    private String currentKey;

    public LocalizedButton(String key) {
        super();
        setKey(key);
        setText(L18n.localize(key));
    }

    public String getKey() {
        return currentKey;
    }

    /**
     * Sets the key property to the new key and updates the text to reflect these changes.
     * A listener is added to listen for changes to the selected language, this allows for the button to update when the language is changed.
     * @param key The new key
     */
    public void setKey(String key) {
        currentKey = key;
        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(L18n.localize(key)));
        setText(L18n.localize(currentKey));
    }
}