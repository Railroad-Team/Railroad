package dev.railroadide.core.ui;

import dev.railroadide.core.settings.keybinds.KeybindData;
import dev.railroadide.core.utility.DesktopUtils;
import javafx.scene.control.MenuItem;

public class RRMenuItem extends MenuItem {
    /**
     * Creates a menu item with the specified text.
     * @param text the text to display on the menu item
     */
    public RRMenuItem(String text) {
        super(text);
    }

    /**
     * Creates a new menu item with the specified text and sets an action to open a URL when clicked.
     * @param text the text to display on the menu item
     * @param url the URL to open when the menu item is clicked
     */
    public RRMenuItem(String text, String url) {
        this(text);
        this.setOnAction($ -> DesktopUtils.openUrl(url));
    }

    /**
     * Sets the associated keybind data for this menu item.
     * @param keybindData the keybind data to associate with this menu item
     */
    public void setKeybindData(KeybindData keybindData) {
        this.setAccelerator(keybindData.getKeyCodeCombination());
    }
}
