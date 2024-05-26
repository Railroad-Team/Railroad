package io.github.railroad.utility.localization;

import io.github.railroad.Railroad;
import io.github.railroad.utility.ConfigHandler;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.InputStream;
import java.util.Properties;

import static io.github.railroad.Railroad.LOGGER;

public class L18n {
    private static final Properties LANG_CACHE = new Properties();
    private static final ObjectProperty<Languages> CURRENT_LANG = new SimpleObjectProperty<>();

    private L18n() {
    }

    public static void setLanguage(Languages language) {
        //Updates the config and calls loadLanguage to update the cache
        LOGGER.debug("Setting language to {}", language);

        var updated = ConfigHandler.getConfigJson();
        updated.get("settings").getAsJsonObject().addProperty("language", language.toString());

        LOGGER.info("Language set to {}", language);
        ConfigHandler.updateConfig(updated);
        loadLanguage();
    }

    public static Languages getCurrentLanguage() {
        return CURRENT_LANG.getValue();
    }

    public static void loadLanguage() {
        //Loads the language into cache and sets the CURRENT_LANG
        LOGGER.info("Loading language file");
        Languages newLang = Languages.valueOf(ConfigHandler.getConfigJson().get("settings").getAsJsonObject().get("language").getAsString());

        if(newLang == null) {
            LOGGER.error("Language not found, defaulting to English");
            newLang = Languages.en_us;
        }

        try {
            InputStream props = Railroad.getResourceAsStream("lang/" + newLang + ".properties");
            LOGGER.info("Reading language file");

            //Load cache and THEN change CURRENT_LANG otherwise binds will be triggered before cache changes
            LANG_CACHE.load(props);
            CURRENT_LANG.setValue(newLang);
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

    public static StringBinding createStringBinding(final String key) {
        return Bindings.createStringBinding(() -> localize(key), CURRENT_LANG);
    }
}