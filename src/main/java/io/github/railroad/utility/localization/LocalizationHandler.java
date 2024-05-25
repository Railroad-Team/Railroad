package io.github.railroad.utility.localization;

import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.utility.ConfigHandler;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.railroad.Railroad.LOGGER;

public class LocalizationHandler {
    private static JsonObject LANG_CACHE;
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
            URI langFile = Railroad.getResource("lang/" + CURRENT_LANG.getLocale() + ".json").toURI();
            Path langPath = Path.of(langFile);
            LOGGER.info("Reading language file {}", langFile);

            LANG_CACHE = Railroad.GSON.fromJson(Files.readString(langPath), JsonObject.class);
        } catch (Exception e) {
            LOGGER.error("Error reading language file", e);
            throw new IllegalStateException("Error reading language file", e);
        }
    }

    public static String getLocalized(String key) {
        LOGGER.info("Getting localized string for key {}", key);
        return LANG_CACHE.get(key).getAsString();
    }
}