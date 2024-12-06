package io.github.railroad.localization;

import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

import static io.github.railroad.Railroad.LOGGER;

/**
 * The L18n class handles the localization for the application.
 * It loads a language file (.lang) and every time a new key is requested, it returns the value from that file.
 * If the file does not include a key, the key is returned instead.
 */
public class L18n {
    /**
     * The language cache, stores key-value pairs to be used for localization.
     * Due to .lang files already being a key-value pair, the properties class parses the file without any need for further parsing.
     */
    private static final Properties LANG_CACHE = new Properties();
    private static final ObjectProperty<Language> CURRENT_LANG = new SimpleObjectProperty<>();

    private L18n() { }

    /**
     * Sets the applications language to the provided language.
     * First updates the config, and then calls loadLanguage to update the cache.
     * @param language The language to change to.
     */
    public static void setLanguage(Language language) {
        // Updates the config and calls loadLanguage to update the cache and CURRENT_LANG
        LOGGER.debug("Setting language to {}", language);

        ConfigHandler.getConfig().getSettings().setLanguage(language);
        ConfigHandler.saveConfig();
        loadLanguage();
    }

    public static Language getCurrentLanguage() {
        return CURRENT_LANG.getValue();
    }

    public static ObjectProperty<Language> currentLanguageProperty() {
        return CURRENT_LANG;
    }

    /**
     * Attempts to load the .lang file for the current language into the cache.
     */
    public static void loadLanguage() {
        // Loads the language into cache and sets the CURRENT_LANG
        LOGGER.info("Loading language file");
        Language language = ConfigHandler.getConfig().getSettings().getLanguage();

        try {
            String name = "lang/" + language.name().toLowerCase(Locale.ROOT) + ".lang";
            InputStream props = Railroad.getResourceAsStream(name);
            System.out.println(name + " " + props);
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
     * Takes in the localization key and returns the localized string.
     * @param key The key to localize.
     * @return The localized string.
     */
    public static String localize(String key) {
        LOGGER.debug("Getting localized string for key {}", key);
        if (LANG_CACHE.get(key) == null) {
            //TODO create a popup/toast to ask if user wants to swap to another language as key is missing
            LOGGER.error("Error finding translations for {} {}", key, CURRENT_LANG);
            return key;
        }

        return LANG_CACHE.get(key).toString();
    }
}