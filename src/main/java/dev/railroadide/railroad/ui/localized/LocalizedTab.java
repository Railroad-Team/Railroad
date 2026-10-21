package dev.railroadide.railroad.ui.localized;

import dev.railroadide.railroad.localization.L18n;
import javafx.scene.Node;
import javafx.scene.control.Tab;

/**
 * An extension of the JavaFX Tab that allows for the Tab's text to be localised.
 */
public class LocalizedTab extends Tab {
    private String currentKey;

    /**
     * Creates a tab with a translated title that updates when the language changes.
     *
     * @param titleKey the localization key for the title
     */
    public LocalizedTab(String titleKey) {
        super();
        setKey(titleKey);
        setText(L18n.localize(titleKey));
    }

    /** Creates an empty tab without a localization key or language-change listener. */
    public LocalizedTab() {
        super();
    }

    /**
     * Creates a tab with a translated title and the supplied content.
     *
     * @param titleKey the localization key for the title
     * @param content the node displayed when the tab is selected
     */
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
     * Sets the key and then updates the tab title.
     * Adds a listener to the current language property to update the text when the language changes.
     *
     * @param key The localization key
     */
    public void setKey(final String key) {
        currentKey = key;
        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) -> setText(L18n.localize(key)));
        setText(L18n.localize(currentKey));
    }
}
