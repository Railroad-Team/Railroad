package io.github.railroad.utility.localization;

import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.utility.ConfigHandler;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static io.github.railroad.Railroad.LOGGER;

public class L18n {
    private static final Properties LANG_CACHE = new Properties();
    private static final ObjectProperty<Language> CURRENT_LANG = new SimpleObjectProperty<>();

    private L18n() {
    }

    public static void setLanguage(Language language) {
        //Updates the config and calls loadLanguage to update the cache and CURRENT_LANG
        LOGGER.debug("Setting language to {}", language);

        JsonObject updated = ConfigHandler.getConfigJson();
        updated.get("settings").getAsJsonObject().addProperty("language", language.toString());

        ConfigHandler.updateConfig();
        loadLanguage();
    }

    public static Language getCurrentLanguage() {
        return CURRENT_LANG.getValue();
    }

    public static ObjectProperty<Language> currentLanguageProperty() {
        return CURRENT_LANG;
    }

    public static void loadLanguage() {
        //Loads the language into cache and sets the CURRENT_LANG
        LOGGER.info("Loading language file");
        var newLang = Language.valueOf(ConfigHandler.getConfigJson().get("settings").getAsJsonObject().get("language").getAsString());

        try {
            InputStream props = Railroad.getResourceAsStream("lang/" + newLang + ".lang");
            LOGGER.info("Reading language file");

            //Load cache and THEN change CURRENT_LANG otherwise binds will be triggered before cache changes
            LANG_CACHE.clear();
            LANG_CACHE.load(props);
            CURRENT_LANG.setValue(newLang);
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