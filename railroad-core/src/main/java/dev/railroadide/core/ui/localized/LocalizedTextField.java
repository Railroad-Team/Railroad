package dev.railroadide.core.ui.localized;

import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.utility.ServiceLocator;
import javafx.scene.control.TextField;

/**
 * An extension of the JavaFX TextField that allows for the TextField's prompt text to be localised.
 */
public class LocalizedTextField extends TextField {
    private String currentKey;

    /**
     * Sets the key and sets the prompt text to the localized key.
     *
     * @param key The key to be localized.
     */
    public LocalizedTextField(final String key) {
        super();
        if (key != null) {
            setKey(key);
            setPromptText(ServiceLocator.getService(LocalizationService.class).get(key));
        }
    }

    /**
     * Gets the current key.
     *
     * @return The current key.
     */
    public String getKey() {
        return currentKey;
    }

    /**
     * Sets the current key, and sets the prompt text to the localized key.
     * Also adds a listener to the current language property to update the text when the language changes.
     *
     * @param key The key to be localized
     */
    public void setKey(final String key) {
        currentKey = key;
        LocalizationService service = ServiceLocator.getService(LocalizationService.class);
        service.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setPromptText(service.get(key)));
        setPromptText(service.get(currentKey));
    }
}
