package dev.railroadide.railroad.ui.localized;

import javafx.scene.control.RadioMenuItem;

/** A {@link RadioMenuItem} whose text is backed by a localization key. */
public class LocalizedRadioMenuItem extends RadioMenuItem {
    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    /**
     * Creates an unselected radio menu item with a translated label.
     *
     * @param key the localization key, or {@code null} for no label
     */
    public LocalizedRadioMenuItem(String key) {
        textProperty().bindBidirectional(localizedText);
        setKey(key);
    }

    /**
     * Returns the key used to translate the menu item's label.
     *
     * @return the localization key, or {@code null} when no key is set
     */
    public String getKey() {
        return localizedText.getTranslationKey();
    }

    /**
     * Changes the localization key and refreshes the label.
     *
     * @param key the localization key; {@code null} or blank clears the label
     */
    public void setKey(String key) {
        localizedText.setTranslationKey(key);
    }
}
