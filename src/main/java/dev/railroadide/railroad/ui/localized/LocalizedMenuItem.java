package dev.railroadide.railroad.ui.localized;

import dev.railroadide.railroad.utility.DesktopUtils;
import dev.railroadide.railroad.settings.keybinds.KeybindData;
import javafx.scene.Node;
import javafx.scene.control.MenuItem;

/**
 * An extension of the JavaFX MenuItem that allows for the MenuItem's text to be localized.
 * It also supports setting a url to open when the item is clicked, additionally allows for a keybind to be set to trigger the items action.
 */
public class LocalizedMenuItem extends MenuItem {

    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    /**
     * Creates a new LocalizedMenuItem with the specified key.
     *
     * @param key The localization key
     */
    public LocalizedMenuItem(final String key) {
        super();
        textProperty().bindBidirectional(localizedText);
        setKey(key);
    }

    /**
     * Creates a new LocalizedMenuItem with the specified key and graphic.
     *
     * @param key     The localization key
     * @param graphic The graphic node to display alongside the text
     */
    public LocalizedMenuItem(final String key, Node graphic) {
        this(key);
        setGraphic(graphic);
    }

    /**
     * Creates a new LocalizedMenuItem with the specified key and URL.
     *
     * @param key The localization key
     * @param url The URL to open when the item is clicked
     */
    public LocalizedMenuItem(final String key, String url) {
        this(key);
        setOnAction($ -> DesktopUtils.openUrl(url));
    }

    /**
     * Returns the current localization key for this menu item.
     *
     * @return The current localization key
     */
    public String getKey() {
        return localizedText.getTranslationKey();
    }

    /**
     * Sets the localization key for this menu item and updates the text accordingly.
     *
     * @param key The new localization key to set
     */
    public void setKey(final String key) {
        localizedText.setTranslationKey(key);
    }

    /**
     * Sets the associated keybind data for this menu item.
     *
     * @param keybindData the keybind data to associate with this menu item
     */
    public void setKeybindData(KeybindData keybindData) {
        setAccelerator(keybindData.getKeyCodeCombination());
    }
}
