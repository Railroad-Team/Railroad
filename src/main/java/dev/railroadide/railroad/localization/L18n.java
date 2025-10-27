package dev.railroadide.railroad.localization;

import dev.railroadide.core.localization.Language;
import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.plugin.PluginManager;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import dev.railroadide.railroadpluginapi.PluginDescriptor;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static dev.railroadide.railroad.Railroad.LOGGER;

/**
 * L18n is a utility class for handling localization.
 * It provides methods to load language files, localize strings, and manage the current language.
 */
public class L18n {
    private static final Properties LANG_CACHE = new Properties();
    private static final ObjectProperty<Language> CURRENT_LANG = new SimpleObjectProperty<>();

    private L18n() {
    }

    /**
     * @return the current Language object
     */
    public static Language getCurrentLanguage() {
        return CURRENT_LANG.getValue();
    }

    /**
     * Returns the current language as an ObjectProperty.
     */
    public static ObjectProperty<Language> currentLanguageProperty() {
        return CURRENT_LANG;
    }

    /**
     * This method is called when a plugin is enabled.
     * It loads the language file for the plugin based on the current language setting.
     *
     * @param descriptor the PluginDescriptor of the plugin being enabled
     */
    public static void onPluginEnabled(PluginDescriptor descriptor) {
        Language language = SettingsHandler.getValue(Settings.LANGUAGE);

        String langFileName = "lang/" + language.getFullCode().toLowerCase(Locale.ROOT) + ".lang";

        LOGGER.debug("Loading language file {} for plugin {}", langFileName, descriptor.getId());
        try {
            InputStream langFileStream = PluginManager.loadResource(descriptor, langFileName);
            if (langFileStream != null) {
                try (var reader = new InputStreamReader(langFileStream, StandardCharsets.UTF_8)) {
                    LANG_CACHE.load(reader);
                }
                LOGGER.debug("Language file {} loaded for plugin {}", langFileName, descriptor.getId());

                CURRENT_LANG.setValue(language);
            }
        } catch (IOException exception) {
            LOGGER.error("Error loading language file {} for plugin {}", langFileName, descriptor.getId(), exception);
        }
    }

    /**
     * Loads the specified language into the cache and sets it as the current language.
     * This method is typically called when the user changes the language setting.
     *
     * @param language the Language object representing the new language
     */
    public static void loadLanguage(Language language) {
        // Loads the language into cache and sets the CURRENT_LANG
        LOGGER.debug("Loading language files");
        try {
            String name = "lang/" + language.getFullCode().toLowerCase(Locale.ROOT) + ".lang";

            LOGGER.debug("Reading language file {}", name);
            List<InputStream> pluginResources = PluginManager.loadResourcesFromAllPlugins(name);
            Properties props = getLanguageProperties(name, pluginResources);

            // Load cache and then change CURRENT_LANG otherwise binds will be triggered before cache changes
            setProps(props);
            CURRENT_LANG.setValue(language);
        } catch (IOException exception) {
            LOGGER.error("Error reading language file", exception);
        }
    }

    /**
     * Loads the language properties from the specified file name.
     * This method is used to load language files from plugins.
     *
     * @param name the name of the language file
     * @return a Properties object containing the loaded language properties
     * @throws IOException if an error occurs while reading the language file
     */
    private static Properties getLanguageProperties(String name, List<InputStream> pluginResources) throws IOException {
        String base = "lang/en_us.lang";

        List<InputStream> streams = new ArrayList<>();

        // setting base language
        InputStream appBase = AppResources.getResourceAsStream(base);
        if (appBase != null) {
            streams.add(appBase);
        }
        List<InputStream> pluginBaseResources = PluginManager.loadResourcesFromAllPlugins(base);
        streams.addAll(pluginBaseResources);

        // setting name language
        InputStream appCurrent = AppResources.getResourceAsStream(name);
        if (appCurrent != null) {
            streams.add(appCurrent);
        }
        streams.addAll(pluginResources);

        return mergeLanguageFiles(streams.toArray(InputStream[]::new));
    }

    private static void setProps(Properties properties) {
        LANG_CACHE.clear();
        LANG_CACHE.putAll(properties);
        LOGGER.debug("Properties set in LANG_CACHE");
    }

    private static Properties mergeLanguageFiles(InputStream... streams) throws IOException {
        var mergedProperties = new Properties();
        for (InputStream stream : streams) {
            if (stream == null)
                continue;

            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                var tempProperties = new Properties();
                tempProperties.load(reader);
                mergedProperties.putAll(tempProperties);
            }
        }

        return mergedProperties;
    }

    /**
     * Localizes a string.
     *
     * @param key the localization key
     * @return the localized string
     */
    public static String localize(String key) {
        if (key == null) {
            LOGGER.error("Localize called with null key");
            return "null";
        }

        if (key.isBlank())
            return "";

        Object value = LANG_CACHE.get(key);
        if (value == null) {
            LOGGER.error("Error finding translations for key '{}' in language {}", key, CURRENT_LANG.getValue());
            return key;
        }

        return value.toString();
    }

    /**
     * Localizes a string and formats it with the given arguments.
     * This is equivalent to String.format(L18n.localize(key), args...).
     *
     * @param key  the localization key
     * @param args the format arguments
     * @return the localized and formatted string
     */
    public static String localize(String key, Object... args) {
        String localizedString = localize(key);
        return String.format(localizedString, args);
    }

    /**
     * Checks if a localization key is valid.
     * A key is considered valid if it exists in the language cache and is not empty.
     *
     * @param key the localization key to check
     * @return true if the key is valid, false otherwise
     */
    public static boolean isKeyValid(String key) {
        Object value = LANG_CACHE.get(key);
        return value != null && !value.toString().isEmpty();
    }
}
