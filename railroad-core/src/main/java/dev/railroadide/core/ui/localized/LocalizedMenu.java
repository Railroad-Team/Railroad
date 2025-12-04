package dev.railroadide.core.ui.localized;

import javafx.scene.control.Menu;

/**
 * An extension of the JavaFX Menu that allows for the Menu's text to be localised.
 */
public class LocalizedMenu extends Menu {
    
    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    /**
     * Creates a new LocalizedMenu with the specified key.
     *
     * @param key The localization key
     */
    public LocalizedMenu(final String key) {
        super();
        textProperty().bindBidirectional(localizedText);
        setKey(key);
    }

    /**
     * Returns the current localization key for this menu.
     *
     * @return The current localization key
     */
    public String getKey() {
        return localizedText.getTranslationKey();
    }

    /**
     * Sets the localization key for this menu and updates the text accordingly.
     *
     * @param key The new localization key to set
     */
    public void setKey(final String key) {
        localizedText.setTranslationKey(key);
    }
}
