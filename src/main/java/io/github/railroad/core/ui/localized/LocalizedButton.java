package io.github.railroad.core.ui.localized;

import io.github.railroad.core.localization.LocalizationServiceLocator;
import javafx.scene.control.Button;

/**
 * An extension of the JavaFX Button that allows for the button's text to be a localized string.
 */
public class LocalizedButton extends Button {
    private String currentKey;

    /**
     * Constructs a LocalizedButton with the specified key.
     * The button's text is set to the localized string corresponding to the key.
     *
     * @param key The localization key for the button's text
     */
    public LocalizedButton(String key) {
        super();
        setKey(key);
        setText(LocalizationServiceLocator.getInstance().get(key));
    }

    /**
     * Gets the current key of the button.
     * This key is used to retrieve the localized text for the button.
     *
     * @return The current key
     */
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
        LocalizationServiceLocator.getInstance().currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(LocalizationServiceLocator.getInstance().get(key)));
        setText(LocalizationServiceLocator.getInstance().get(currentKey));
    }
}