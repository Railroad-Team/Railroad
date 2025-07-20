package io.github.railroad.localization;

import io.github.railroad.Railroad;
import io.github.railroad.core.localization.Language;
import io.github.railroad.plugin.PluginManager;
import io.github.railroad.railroadpluginapi.PluginDescriptor;
import io.github.railroad.settings.Settings;
import io.github.railroad.settings.handler.SettingsHandler;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import static io.github.railroad.Railroad.LOGGER;

/**
 * L18n is a utility class for handling localization.
 * It provides methods to load language files, localize strings, and manage the current language.
 */
public class L18n {
    private static final Properties LANG_CACHE = new Properties();
    private static final ObjectProperty<Language> CURRENT_LANG = new SimpleObjectProperty<>();

    private L18n() {}

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
                LANG_CACHE.load(langFileStream);
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
        var languageFiles = new InputStream[pluginResources.size() + 1];
        languageFiles[0] = Railroad.getResourceAsStream(name);
        for (int i = 0; i < pluginResources.size(); i++) {
            languageFiles[i + 1] = pluginResources.get(i);
        }

        Properties props = mergeLanguageFiles(languageFiles);
        for (InputStream stream : languageFiles) {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException exception) {
                    LOGGER.error("Error closing language file stream", exception);
                }
            }
        }

        return props;
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

            mergedProperties.load(stream);
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
        return LANG_CACHE.get(key) != null && LANG_CACHE.get(key) != "";
    }
}