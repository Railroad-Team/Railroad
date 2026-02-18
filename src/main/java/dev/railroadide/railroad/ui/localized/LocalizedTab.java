package dev.railroadide.railroad.ui.localized;

import dev.railroadide.railroad.localization.L18n;
import javafx.scene.Node;
import javafx.scene.control.Tab;

/**
 * An extension of the JavaFX Tab that allows for the Tab's text to be localised.
 */
public class LocalizedTab extends Tab {
    private String currentKey;

    public LocalizedTab(String titleKey) {
        super();
        setKey(titleKey);
        setText(L18n.localize(titleKey));
    }

    public LocalizedTab() {
        super();
    }

    public LocalizedTab(String titleKey, Node content) {
        this(titleKey);
        setContent(content);
    }

    /**
     * Gets the current key used for localization.
     *
     * @return The current localization key.
     */
    public String getKey() {
        return currentKey;
    }

    /**
     * Sets the key and then updates the text of the label.
     * Adds a listener to the current language property to update the text when the language changes.
     *
     * @param key The localization key
     */
    public void setKey(final String key) {
        currentKey = key;
        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
            setText(L18n.localize(key)));
        setText(L18n.localize(currentKey));
    }
}
