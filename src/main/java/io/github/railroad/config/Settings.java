package io.github.railroad.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.github.railroad.Railroad;
import io.github.railroad.localization.Language;
import io.github.railroad.plugin.Plugin;
import io.github.railroad.utility.JsonSerializable;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Stores the settings for the application.
 */
public class Settings implements JsonSerializable<JsonObject> {
    private final ObservableMap<String, PluginSettings> pluginsSettings = FXCollections.observableHashMap();
    private final StringProperty theme = new SimpleStringProperty("default-dark");
    private final ObjectProperty<Language> language = new SimpleObjectProperty<>(Language.EN_US);

    @Override
    public JsonObject toJson() {
        var json = new JsonObject();

        var pluginsSettings = new JsonArray();
        for (Map.Entry<String, PluginSettings> entry : this.pluginsSettings.entrySet()) {
            var object = new JsonObject();
            object.addProperty("Plugin", entry.getKey());
            object.add("Settings", entry.getValue().toJson());
            pluginsSettings.add(object);
        }

        json.add("PluginsSettings", pluginsSettings);

        json.addProperty("Theme", this.theme.get());
        json.addProperty("Language", this.language.get().name().toLowerCase(Locale.ROOT));

        return json;
    }

    @Override
    public void fromJson(JsonObject json) {
        if (json.has("PluginsSettings")) {
            JsonElement pluginsSettings = json.get("PluginsSettings");
            if (pluginsSettings.isJsonArray()) {
                for (JsonElement element : pluginsSettings.getAsJsonArray()) {
                    if (element.isJsonObject()) {
                        JsonObject object = element.getAsJsonObject();
                        if (!object.has("Name") || !object.get("Name").isJsonPrimitive())
                            continue;

                        JsonPrimitive name = object.getAsJsonPrimitive("Name");
                        if (!name.isString())
                            continue;

                        Optional<Plugin> plugin = Railroad.PLUGIN_MANAGER.byName(name.getAsString());
                        if (plugin.isEmpty())
                            continue;

                        this.pluginsSettings.put(name.getAsString(), plugin.get().createSettings());
                    }
                }
            }
        }

        Railroad.PLUGIN_MANAGER.getPluginList().forEach(plugin -> {
            this.pluginsSettings.computeIfAbsent(plugin.getName(), name -> plugin.createSettings());
        });

        if (json.has("Theme") && json.get("Theme").isJsonPrimitive()) {
            JsonPrimitive theme = json.getAsJsonPrimitive("Theme");
            if (theme.isString())
                this.theme.set(theme.getAsString());
        }

        if (json.has("Language") && json.get("Language").isJsonPrimitive()) {
            JsonPrimitive language = json.getAsJsonPrimitive("Language");
            if (language.isString()) {
                try {
                    this.language.set(Language.valueOf(language.getAsString().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    /**
     * Copies the settings from the given settings object to this object.
     * @param settings {@link Settings} - The settings to copy from.
     */
    public void copyFrom(Settings settings) {
        this.pluginsSettings.clear();
        this.pluginsSettings.putAll(settings.pluginsSettings);

        this.theme.set(settings.theme.get());
        this.language.set(settings.language.get());
    }

    /**
     * Gets the settings for the given plugin.
     * @param plugin {@link Plugin} - The plugin to get the settings for.
     * @return {@link PluginSettings} - The settings for the plugin.
     */
    public PluginSettings getPluginSettings(Plugin plugin) {
        return this.pluginsSettings.get(plugin.getName());
    }

    /**
     * Gets the settings for the given plugin.
     * @param plugin {@link Plugin} - The plugin to get the settings for.
     * @param settingsClass {@link Class} - The class of the settings.
     * @return {@link T} - The settings for the plugin.
     * @param <T> The type of the settings.
     * @throws ClassCastException If the settings cannot be cast to the given class.
     */
    public <T extends PluginSettings> T getPluginSettings(Plugin plugin, Class<? extends T> settingsClass) throws ClassCastException {
        return getPluginSettings(plugin.getName(), settingsClass);
    }

    /**
     * Gets the settings for the given plugin.
     * @param pluginName {@link String} - The name of the plugin to get the settings for.
     * @param settingsClass {@link Class} - The class of the settings.
     * @return {@link T} - The settings for the plugin.
     * @param <T> The type of the settings.
     * @throws ClassCastException If the settings cannot be cast to the given class.
     */
    public <T extends PluginSettings> T getPluginSettings(String pluginName, Class<? extends T> settingsClass) throws ClassCastException {
        return settingsClass.cast(this.pluginsSettings.get(pluginName));
    }

    public String getTheme() {
        return theme.get();
    }

    public void setTheme(String theme) {
        this.theme.set(theme == null ? "default-dark" : theme);
    }

    public Language getLanguage() {
        return language.get();
    }

    public void setLanguage(Language language) {
        this.language.set(language == null ? Language.EN_US : language);
    }
}
