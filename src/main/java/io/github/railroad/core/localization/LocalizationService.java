package io.github.railroad.core.localization;

import javafx.beans.property.ObjectProperty;

/**
 * LocalizationService is an interface for retrieving localized strings.
 * It allows plugins to access localized messages using a key and optional arguments.
 */
public interface LocalizationService {
    /**
     * Retrieves a localized string based on the provided key.
     * If the key is not found, it returns the key itself.
     *
     * @param key  the key for the localized string
     * @param args optional arguments to format the localized string
     * @return the localized string or the key if not found
     */
    String get(String key, Object... args);

    /**
     * Retrieves the current language setting.
     *
     * @return the current Language object
     */
    ObjectProperty<? extends Language> currentLanguageProperty();

    /**
     * Checks if a given key is valid and exists in the localization resources.
     *
     * @param key the key to check
     * @return true if the key is valid, false otherwise
     */
    boolean isKeyValid(String key);
}
