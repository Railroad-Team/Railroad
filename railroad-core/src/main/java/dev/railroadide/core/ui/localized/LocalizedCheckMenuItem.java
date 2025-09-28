package dev.railroadide.core.ui.localized;

import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.utility.ServiceLocator;
import javafx.scene.control.CheckMenuItem;

/**
 * An extension of the JavaFX CheckMenuItem that allows for the CheckMenuItem's text to be localized.
 */
public class LocalizedCheckMenuItem extends CheckMenuItem {
    private String currentKey;

    /**
     * Creates a new LocalizedCheckMenuItem with the specified key.
     * @param key The localization key
     * @param selected Whether the item should be selected by default
     */
    public LocalizedCheckMenuItem(String key, boolean selected) {
        super();
        setKey(key);
        setSelected(selected);
        setText(ServiceLocator.getService(LocalizationService.class).get(key));
    }

    /**
     * Returns the current localization key.
     * @return the current localization key
     */
    public String getKey() {
        return currentKey;
    }

    /**
     * Sets the localization key and updates the text accordingly.
     * @param key the new localization key to set
     */
    public void setKey(final String key) {
        currentKey = key;
        ServiceLocator.getService(LocalizationService.class).currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(ServiceLocator.getService(LocalizationService.class).get(key)));
        setText(ServiceLocator.getService(LocalizationService.class).get(currentKey));
    }
}
