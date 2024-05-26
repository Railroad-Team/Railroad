package io.github.railroad.utility.localization;

import io.github.railroad.Railroad;
import io.github.railroad.utility.ConfigHandler;

import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;

import static io.github.railroad.Railroad.LOGGER;

public class L18n {
    private static final Properties LANG_CACHE = new Properties();
    private static Languages CURRENT_LANG;

    private L18n() {
    }

    public static void setLanguage(Languages language) {
        //Updates the config and calls loadLanguage to update the cache
        LOGGER.debug("Setting language to {}", language);

        var updated = ConfigHandler.getConfigJson();
        updated.get("settings").getAsJsonObject().addProperty("language", language.toString());

        ConfigHandler.updateConfig(updated);
        loadLanguage();

        LOGGER.info("Language set to {}", language);
    }

    public static Languages getCurrentLanguage() {
        return CURRENT_LANG;
    }

    public static void loadLanguage() {
        //Loads the language into cache and sets the CURRENT_LANG
        LOGGER.info("Loading language file");
        CURRENT_LANG = Languages.valueOf(ConfigHandler.getConfigJson().get("settings").getAsJsonObject().get("language").getAsString());

        if(CURRENT_LANG == null) {
            LOGGER.error("Language not found, defaulting to English");
            CURRENT_LANG = Languages.en_us;
        }

        try {
            LOGGER.debug(String.valueOf(CURRENT_LANG));
            InputStream props = Railroad.getResourceAsStream("lang/" + CURRENT_LANG.toString() + ".properties");
            LOGGER.info("Reading language file");

            LANG_CACHE.load(props);
        } catch (Exception e) {
            LOGGER.error("Error reading language file", e);
            throw new IllegalStateException("Error reading language file", e);
        }
    }

    public static String localize(String key) {
        LOGGER.debug("Getting localized string for key {}", key);
        if (LANG_CACHE.get(key) == null) {
            LOGGER.error("Error finding translations for {} {} Moving to english", key, CURRENT_LANG);

            setLanguage(Languages.en_us);
            return localize(key);
        }
        return LANG_CACHE.get(key).toString();
    }
}