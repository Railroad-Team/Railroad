package dev.railroadide.railroad.ui.localized;

import javafx.scene.control.RadioMenuItem;

/** A {@link RadioMenuItem} whose text is backed by a localization key. */
public class LocalizedRadioMenuItem extends RadioMenuItem {
    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    public LocalizedRadioMenuItem(String key) {
        textProperty().bindBidirectional(localizedText);
        setKey(key);
    }

    public String getKey() {
        return localizedText.getTranslationKey();
    }

    public void setKey(String key) {
        localizedText.setTranslationKey(key);
    }
}
