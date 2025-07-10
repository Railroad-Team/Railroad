package io.github.railroad.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.plugin.defaults.DefaultPluginContext;
import io.github.railroad.railroadpluginapi.Plugin;
import io.github.railroad.railroadpluginapi.PluginDescriptor;
import io.github.railroad.settings.handler.Settings;
import io.github.railroad.settings.handler.SettingsHandler;
import io.github.railroad.utility.ShutdownHooks;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PluginManager is responsible for loading, enabling, and disabling plugins in the Railroad application.
 * It scans a specified directory for JAR files containing plugins, loads them, and manages their lifecycle.
 */
public class PluginManager {
    private static final ObservableList<PluginLoadResult> LOADED_PLUGINS = FXCollections.observableArrayList();

    private static List<PluginDescriptor> readyToLoad;

    /**
     * Loads all plugins from the specified directory.
     * The directory should contain JAR files with valid plugin descriptors.
     *
     * @param directory The directory containing plugin JAR files.
     * @throws IllegalArgumentException if the directory is null, does not exist, or is not a directory.
     */
    public static void loadPlugins(@NotNull Path directory) {
        if (directory == null || (Files.exists(directory) && !Files.isDirectory(directory)))
            throw new IllegalArgumentException("Invalid plugin directory: " + directory);

        if (Files.notExists(directory)) {
            try {
                Files.createDirectories(directory);
            } catch (IOException exception) {
                Railroad.LOGGER.error("Failed to create plugin directory: {}", directory.toAbsolutePath(), exception);
                return;
            }

            Railroad.LOGGER.info("Plugin directory does not exist, created: {}", directory.toAbsolutePath());
        }

        boolean firstLoad = false;
        if (readyToLoad == null) {
            readyToLoad = new ArrayList<>();
            firstLoad = true;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.jar")) {
            for (Path entry : stream) {
                if (!Files.isRegularFile(entry) || !Files.isReadable(entry)) {
                    Railroad.LOGGER.warn("Skipping non-regular file or unreadable file: {}", entry.toAbsolutePath());
                    continue;
                }

                try {
                    PluginLoadResult loadResult = PluginLoader.loadPlugin(entry);
                    PluginDescriptor descriptor = loadResult.descriptor();

                    Railroad.LOGGER.info("Found plugin: {}", descriptor.getName());

                    LOADED_PLUGINS.add(loadResult);
                    if (firstLoad) {
                        readyToLoad.add(descriptor);
                    } else {
                        addPluginToSettings(descriptor);
                    }
                } catch (Exception exception) {
                    Railroad.LOGGER.error("Failed to load plugin from {}", entry.toAbsolutePath(), exception);
                }
            }
        } catch (Exception exception) {
            Railroad.LOGGER.error("Failed to load plugins from directory: {}", directory.toAbsolutePath(), exception);
        }
    }

    public static void enableEnabledPlugins() {
        Map<PluginDescriptor, Boolean> enabledPlugins = getEnabledPlugins();
        if (enabledPlugins.isEmpty()) return;

        for (PluginDescriptor descriptor : enabledPlugins.entrySet()
                .stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList()) {
            try {
                enablePlugin(descriptor);
            } catch (Exception exception) {
                Railroad.LOGGER.error("Failed to enable plugin: {}", descriptor.getName(), exception);
            }
        }
    }

    public static void loadReadyPlugins() {
        if (readyToLoad == null || readyToLoad.isEmpty()) {
            Railroad.LOGGER.info("No plugins ready to load");
            return;
        }

        for (PluginDescriptor descriptor : readyToLoad) {
            addPluginToSettings(descriptor);
        }

        readyToLoad.clear();
    }

    /**
     * Adds a plugin descriptor to the settings for tracking enabled plugins.
     * This method ensures that the plugin is registered in the settings even if it is not enabled.
     *
     * @param descriptor The PluginDescriptor of the plugin to add.
     */
    private static void addPluginToSettings(PluginDescriptor descriptor) {
        Map<PluginDescriptor, Boolean> enabledPlugins = getEnabledPlugins();
        if (!enabledPlugins.containsKey(descriptor)) {
            enabledPlugins.put(descriptor, false);
            SettingsHandler.setValue(Settings.ENABLED_PLUGINS, enabledPlugins);
        }
    }

    /**
     * Returns an observable list of all loaded plugins.
     *
     * @return ObservableList of PluginLoadResult objects representing the loaded plugins.
     */
    public static ObservableList<PluginLoadResult> getLoadedPluginsList() {
        return LOADED_PLUGINS;
    }

    /**
     * Enables a plugin by its descriptor.
     * The plugin must be loaded before it can be enabled.
     *
     * @param descriptor The PluginDescriptor of the plugin to enable.
     * @throws IllegalArgumentException if the descriptor is null or the plugin is not found.
     */
    public static void enablePlugin(PluginDescriptor descriptor) {
        if (descriptor == null)
            throw new IllegalArgumentException("PluginDescriptor cannot be null");

        PluginLoadResult loadResult = LOADED_PLUGINS.stream()
                .filter(result -> result.descriptor().equals(descriptor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + descriptor.getName()));

        Path pluginPath = loadResult.pluginPath();
        try {
            var classLoader = new PluginClassLoader(pluginPath);
            Class<?> pluginClass = classLoader.loadClass(descriptor.getMainClass());
            if (!Plugin.class.isAssignableFrom(pluginClass))
                throw new IllegalArgumentException("Main class does not implement Plugin interface: " + descriptor.getMainClass());

            Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();

            var context = new DefaultPluginContext(descriptor, Railroad.EVENT_BUS);

            plugin.onEnable(context);
            loadResult.setPlugin(plugin, classLoader);
            ShutdownHooks.addHook(() -> {
                try {
                    plugin.onDisable(context); // TODO: Have a terminate method in Plugin interface
                    classLoader.close();
                    loadResult.setPlugin(null, null);
                } catch (Exception exception) {
                    Railroad.LOGGER.error("Error during plugin {} onDisable", descriptor.getName(), exception);
                }
            });

            Map<PluginDescriptor, Boolean> enabledPlugins = getEnabledPlugins();
            enabledPlugins.put(descriptor, true);
            SettingsHandler.setValue(Settings.ENABLED_PLUGINS, enabledPlugins);

            Railroad.LOGGER.info("Enabled plugin: {}", descriptor.getName());
        } catch (Exception exception) {
            throw new RuntimeException("Failed to instantiate plugin: " + descriptor.getName(), exception);
        }
    }

    /**
     * Disables a plugin by its descriptor.
     * The plugin must be enabled before it can be disabled.
     *
     * @param descriptor The PluginDescriptor of the plugin to disable.
     * @throws IllegalArgumentException if the descriptor is null or the plugin is not found.
     */
    public static void disablePlugin(PluginDescriptor descriptor) {
        if (descriptor == null)
            throw new IllegalArgumentException("PluginDescriptor cannot be null");

        PluginLoadResult loadResult = LOADED_PLUGINS.stream()
                .filter(result -> result.descriptor().equals(descriptor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + descriptor.getName()));

        if (!PluginManager.isPluginEnabledForce(descriptor)) {
            Railroad.LOGGER.warn("Plugin {} is not enabled, cannot disable", descriptor.getName());
            return;
        }

        try {
            Plugin plugin = loadResult.pluginInstance();
            if (plugin != null) {
                var context = new DefaultPluginContext(descriptor, Railroad.EVENT_BUS);
                plugin.onDisable(context);

                loadResult.classLoader().close();
                loadResult.setPlugin(null, null);
            } else {
                Railroad.LOGGER.warn("Plugin instance for {} is null, skipping onDisable", descriptor.getName());
            }

            Map<PluginDescriptor, Boolean> enabledPlugins = getEnabledPlugins();
            enabledPlugins.put(descriptor, false);
            SettingsHandler.setValue(Settings.ENABLED_PLUGINS, enabledPlugins);

            Railroad.LOGGER.info("Disabled plugin: {}", descriptor.getName());
        } catch (Exception exception) {
            throw new RuntimeException("Failed to disable plugin: " + descriptor.getName(), exception);
        }
    }

    public static boolean isPluginEnabled(PluginDescriptor descriptor) {
        if (descriptor == null)
            throw new IllegalArgumentException("PluginDescriptor cannot be null");

        Map<PluginDescriptor, Boolean> enabledPlugins = SettingsHandler.getValue(Settings.ENABLED_PLUGINS);
        if (enabledPlugins == null)
            return false;

        return enabledPlugins.getOrDefault(descriptor, false);
    }

    public static boolean isPluginEnabledForce(PluginDescriptor descriptor) {
        if (descriptor == null)
            throw new IllegalArgumentException("PluginDescriptor cannot be null");

        return LOADED_PLUGINS.stream()
                .anyMatch(result ->
                        result.descriptor().equals(descriptor) &&
                                result.pluginInstance() != null &&
                                result.classLoader() != null);
    }

    public static void setEnabledPlugins(Map<PluginDescriptor, Boolean> enabledPlugins) {
        if (enabledPlugins == null)
            throw new IllegalArgumentException("Enabled plugins map cannot be null");

        for (Map.Entry<PluginDescriptor, Boolean> entry : enabledPlugins.entrySet()) {
            PluginDescriptor descriptor = entry.getKey();
            boolean enabled = entry.getValue();

            if (enabled) {
                enablePlugin(descriptor);
            } else {
                disablePlugin(descriptor);
            }

            Railroad.LOGGER.info("Set plugin {} to {}", descriptor.getName(), enabled ? "enabled" : "disabled");
        }
    }

    /**
     * Retrieves the currently enabled plugins from the settings.
     * If no plugins are enabled, it returns an empty map.
     *
     * @return A map of PluginDescriptor to their enabled status (true for enabled, false for disabled).
     */
    public static Map<PluginDescriptor, Boolean> getEnabledPlugins() {
        Map<PluginDescriptor, Boolean> enabledPlugins = SettingsHandler.getValue(Settings.ENABLED_PLUGINS);
        if (enabledPlugins == null) {
            enabledPlugins = new HashMap<>();
        }

        return enabledPlugins;
    }

    public static JsonElement encodeEnabledPlugins(Map<PluginDescriptor, Boolean> enabledPlugins) {
        if (enabledPlugins == null)
            throw new IllegalArgumentException("Enabled plugins map cannot be null");

        var jsonObject = new JsonObject();
        for (Map.Entry<PluginDescriptor, Boolean> entry : enabledPlugins.entrySet()) {
            jsonObject.addProperty(entry.getKey().getId(), entry.getValue());
        }

        return jsonObject;
    }

    public static Map<PluginDescriptor, Boolean> decodeEnabledPlugins(JsonElement json) {
        if (json == null || !json.isJsonObject())
            throw new IllegalArgumentException("Invalid JSON for enabled plugins");

        Map<PluginDescriptor, Boolean> enabledPlugins = new HashMap<>();
        JsonObject jsonObject = json.getAsJsonObject();

        for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
            String pluginId = entry.getKey();
            boolean isEnabled = entry.getValue().getAsBoolean();

            PluginDescriptor descriptor = LOADED_PLUGINS.stream()
                    .map(PluginLoadResult::descriptor)
                    .filter(pd -> pd.getId().equals(pluginId))
                    .findFirst()
                    .orElse(null);

            if (descriptor != null) {
                enabledPlugins.put(descriptor, isEnabled);
            } else {
                Railroad.LOGGER.warn("Plugin with ID {} not found in registry", pluginId);
            }
        }

        return enabledPlugins;
    }
}
