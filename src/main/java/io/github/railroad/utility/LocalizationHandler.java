package io.github.railroad.utility;

import com.google.gson.JsonObject;
import io.github.railroad.Railroad;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.railroad.Railroad.LOGGER;

public class LocalizationHandler {

    private LocalizationHandler() {
    }

    public static String convertLanguage(String language, boolean toLocale) {
        if(toLocale) {
            return switch (language) {
                case "english" -> "en_us";
                case "español" -> "es_es";
                case "français" -> "fr_fr";
                case "deutsch" -> "de_de";
                default -> throw new IllegalStateException("Unexpected value: " + language);
            };
        } else {
            return switch (language) {
                case "en_us" -> "English";
                case "es_es" -> "Español";
                case "fr_fr" -> "Français";
                case "de_de" -> "Deutsch";
                default -> throw new IllegalStateException("Unexpected value: " + language);
            };
        }
    }

    public static void setLanguage(String language) {
        String lang = convertLanguage(language.toLowerCase(), true);

        var updated = ConfigHandler.getConfigJson();
        updated.get("settings").getAsJsonObject().addProperty("language", lang);

        ConfigHandler.updateConfig(updated);
        LOGGER.info("Language set to {}", lang);
    }

    public static String getCurrentLanguage() {
        return ConfigHandler.getConfigJson().get("settings").getAsJsonObject().get("language").getAsString();
    }

    public static String getLocalized(String key) {
        try {
            URI langFile = Railroad.getResource("lang/" + getCurrentLanguage() + ".json").toURI();
            Path langPath = Path.of(langFile);
            LOGGER.info("Reading language file {}", langFile);

            JsonObject file = Railroad.GSON.fromJson(Files.readString(langPath), JsonObject.class);
            return file.get(key).getAsString();
        } catch (Exception e) {
            LOGGER.error("Error reading language file, looking for key {}", key, e);
            throw new IllegalStateException("Error reading language file", e);
        }
    }
}