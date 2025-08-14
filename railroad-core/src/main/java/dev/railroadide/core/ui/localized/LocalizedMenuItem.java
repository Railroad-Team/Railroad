package dev.railroadide.core.ui.localized;

import dev.railroadide.core.localization.LocalizationServiceLocator;
import dev.railroadide.core.settings.keybinds.KeybindData;
import dev.railroadide.core.utility.DesktopUtils;
import javafx.scene.control.MenuItem;

/**
 * An extension of the JavaFX MenuItem that allows for the MenuItem's text to be localized.
 * It also supports setting a url to open when the item is clicked, additionally allows for a keybind to be set to trigger the items action.
 */
public class LocalizedMenuItem extends MenuItem {
    private String currentKey;

    /**
     * Creates a new LocalizedMenuItem with the specified key.
     * @param key The localization key
     */
    public LocalizedMenuItem(final String key) {
        super();
        setKey(key);
        setText(LocalizationServiceLocator.getInstance().get(key));
    }

    /**
     * Creates a new LocalizedMenuItem with the specified key and URL.
     * @param key The localization key
     * @param url The URL to open when the item is clicked
     */
    public LocalizedMenuItem(final String key, String url) {
        this(key);
        this.setOnAction($ -> DesktopUtils.openUrl(url));
    }

    /**
     * Returns the current localization key for this menu item.
     * @return The current localization key
     */
    public String getKey() {
        return currentKey;
    }

    /**
     * Sets the localization key for this menu item and updates the text accordingly.
     * @param key The new localization key to set
     */
    public void setKey(final String key) {
        currentKey = key;
        LocalizationServiceLocator.getInstance().currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(LocalizationServiceLocator.getInstance().get(key)));
        setText(LocalizationServiceLocator.getInstance().get(currentKey));
    }

    /**
     * Sets the associated keybind data for this menu item.
     * @param keybindData the keybind data to associate with this menu item
     */
    public void setKeybindData(KeybindData keybindData) {
        this.setAccelerator(keybindData.getKeyCodeCombination());
    }
}
