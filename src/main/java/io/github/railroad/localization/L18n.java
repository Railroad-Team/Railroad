package io.github.railroad.localization;

import io.github.railroad.Railroad;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

import static io.github.railroad.Railroad.LOGGER;
import static io.github.railroad.Railroad.SETTINGS_HANDLER;

public class L18n {
    private static final Properties LANG_CACHE = new Properties();
    private static final ObjectProperty<Language> CURRENT_LANG = new SimpleObjectProperty<>();

    private L18n() {
    }

    public static Language getCurrentLanguage() {
        return CURRENT_LANG.getValue();
    }

    public static ObjectProperty<Language> currentLanguageProperty() {
        return CURRENT_LANG;
    }

    public static void loadLanguage() {
        // Loads the language into cache and sets the CURRENT_LANG
        LOGGER.info("Loading language file");
        Language language = SETTINGS_HANDLER.getSettingValue("railroad:language", Language.class);

        try {
            String name = "lang/" + language.name().toLowerCase(Locale.ROOT) + ".lang";
            InputStream props = Railroad.getResourceAsStream(name);
            LOGGER.info("Reading language file {}", name);

            // Load cache and then change CURRENT_LANG otherwise binds will be triggered before cache changes
            LANG_CACHE.clear();
            LANG_CACHE.load(props);
            CURRENT_LANG.setValue(language);
        } catch (IOException exception) {
            LOGGER.error("Error reading language file", exception);
            throw new IllegalStateException("Error reading language file", exception);
        }
    }

    /**
     * Localizes a string.
     *
     * @param key the localization key
     * @return the localized string
     */
    public static String localize(String key) {
        LOGGER.debug("Getting localized string for key {}", key);

        if (key == null) {
            LOGGER.error("Localize called with null key");
            return "null";
        }
        
        if (LANG_CACHE.get(key) == null) {
            //TODO create a popup/toast to ask if user wants to swap to english as key is missing
            LOGGER.error("Error finding translations for key '{}' in language {}", key, CURRENT_LANG.getValue());
            return key;
        }

        return LANG_CACHE.get(key).toString();
    }

    /**
     * Localizes a string and formats it with the given arguments.
     * This is equivalent to String.format(L18n.localize(key), args...).
     *
     * @param key the localization key
     * @param args the format arguments
     * @return the localized and formatted string
     */
    public static String localize(String key, Object... args) {
        String localizedString = localize(key);
        return String.format(localizedString, args);
    }

    public static boolean isKeyValid(String key) {
        return LANG_CACHE.get(key) != null && LANG_CACHE.get(key) != "";
    }
}