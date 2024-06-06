package io.github.railroad.utility.localization;

import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

import static io.github.railroad.Railroad.LOGGER;

public class L18n {
    private static final Properties LANG_CACHE = new Properties();
    private static final ObjectProperty<Language> CURRENT_LANG = new SimpleObjectProperty<>();

    private L18n() {
    }

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

    public static String localize(String key) {
        LOGGER.debug("Getting localized string for key {}", key);
        if (LANG_CACHE.get(key) == null) {
            LOGGER.error("Error finding translations for {} {} Moving to english", key, CURRENT_LANG);

            setLanguage(Language.EN_US);
            return localize(key);
        }

        return LANG_CACHE.get(key).toString();
    }
}