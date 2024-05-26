package io.github.railroad.utility.localization;

import io.github.railroad.Railroad;
import io.github.railroad.utility.ConfigHandler;

import java.io.InputStream;
import java.util.Properties;

import static io.github.railroad.Railroad.LOGGER;

public class LocalizationHandler {
    private static Properties LANG_CACHE = new Properties();
    private static Languages CURRENT_LANG;

    private LocalizationHandler() {
    }

    public static void setLanguage(Languages language) {
        //Updates the config and calls loadLanguage to update the cache
        LOGGER.debug("Setting language to {}", language);

        var updated = ConfigHandler.getConfigJson();
        updated.get("settings").getAsJsonObject().addProperty("language", language.getLocale());

        ConfigHandler.updateConfig(updated);
        loadLanguage();

        LOGGER.info("Language set to {}", language.getLocale());
    }

    public static Languages getCurrentLanguage() {
        return CURRENT_LANG;
    }

    public static void loadLanguage() {
        //Loads the language into cache and sets the CURRENT_LANG
        LOGGER.info("Loading language file");
        CURRENT_LANG = Languages.fromLocale(ConfigHandler.getConfigJson().get("settings").getAsJsonObject().get("language").getAsString());

        if(CURRENT_LANG == null) {
            CURRENT_LANG = Languages.ENGLISH;
            LOGGER.error("Language {} not found, defaulting to English");
        }

        try {
            InputStream props = Railroad.getResourceAsStream("lang/" + CURRENT_LANG.getLocale() + ".properties");
            LOGGER.info("Reading language file {}", props.toString());

            LANG_CACHE.load(props);
        } catch (Exception e) {
            LOGGER.error("Error reading language file", e);
            throw new IllegalStateException("Error reading language file", e);
        }
    }

    public static String getLocalized(String key) {
        LOGGER.info("Getting localized string for key {}", key);
        if (LANG_CACHE.get(key) == null) {
            LOGGER.error("Error finding translations for {} {} Moving to english", key, CURRENT_LANG);

            setLanguage(Languages.ENGLISH);
            getLocalized(key);
        }
        return LANG_CACHE.get(key).toString();
    }
}