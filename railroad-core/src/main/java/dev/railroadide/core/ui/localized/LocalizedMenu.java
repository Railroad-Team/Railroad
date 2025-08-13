package dev.railroadide.core.ui.localized;

import dev.railroadide.core.localization.LocalizationServiceLocator;
import javafx.scene.control.Menu;

/**
 * An extension of the JavaFX Menu that allows for the Menu's text to be localised.
 */
public class LocalizedMenu extends Menu {
    private String currentKey;

    /**
     * Creates a new LocalizedMenu with the specified key.
     * @param key The localization key
     */
    public LocalizedMenu(final String key) {
        super();
        setKey(key);
        setText(LocalizationServiceLocator.getInstance().get(key));
    }

    /**
     * Returns the current localization key for this menu.
     * @return The current localization key
     */
    public String getKey() {
        return currentKey;
    }

    /**
     * Sets the localization key for this menu and updates the text accordingly.
     * @param key The new localization key to set
     */
    public void setKey(final String key) {
        currentKey = key;
        LocalizationServiceLocator.getInstance().currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(LocalizationServiceLocator.getInstance().get(key)));
        setText(LocalizationServiceLocator.getInstance().get(currentKey));
    }
}
