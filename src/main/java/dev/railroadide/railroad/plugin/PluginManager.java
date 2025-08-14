package dev.railroadide.railroad.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.defaults.DefaultPluginContext;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import dev.railroadide.railroad.utility.ShutdownHooks;
import dev.railroadide.railroadpluginapi.Plugin;
import dev.railroadide.railroadpluginapi.PluginDescriptor;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
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

    /**
     * Enables all plugins that are currently marked as enabled in the settings.
     * This method iterates through the enabled plugins and calls enablePlugin for each one.
     * It logs any errors encountered during the enabling process.
     */
    public static void enableEnabledPlugins() {
        Map<PluginDescriptor, Boolean> enabledPlugins = getEnabledPlugins();
        if (enabledPlugins.isEmpty())
            return;

        for (PluginDescriptor descriptor : enabledPlugins.entrySet()
                .stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .toList()) {
            if (PluginManager.isPluginEnabledForce(descriptor))
                continue; // Skip if already enabled

            try {
                enablePlugin(descriptor);
            } catch (Exception exception) {
                Railroad.LOGGER.error("Failed to enable plugin: {}", descriptor.getName(), exception);
            }
        }
    }

    /**
     * Loads all plugins that are ready to be loaded.
     * This method should be called after all plugins have been scanned and their descriptors are ready.
     * It enables the plugins and registers them in the settings.
     */
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
            var classLoader = new PluginClassLoader(pluginPath, descriptor.getDependencies());
            Class<?> pluginClass = classLoader.loadClass(descriptor.getMainClass());
            if (!Plugin.class.isAssignableFrom(pluginClass))
                throw new IllegalArgumentException("Main class does not implement Plugin interface: " + descriptor.getMainClass());

            Plugin plugin = (Plugin) pluginClass.getDeclaredConstructor().newInstance();

            var context = new DefaultPluginContext(descriptor, Railroad.EVENT_BUS);

            plugin.onEnable(context);
            loadResult.setPlugin(plugin, classLoader);
            ShutdownHooks.addHook(() -> {
                try {
                    classLoader.close();
                    loadResult.setPlugin(null, null);
                } catch (Exception exception) {
                    Railroad.LOGGER.error("Error during plugin {} onDisable", descriptor.getName(), exception);
                }
            });

            Map<PluginDescriptor, Boolean> enabledPlugins = getEnabledPlugins();
            enabledPlugins.put(descriptor, true);
            SettingsHandler.setValue(Settings.ENABLED_PLUGINS, enabledPlugins);

            L18n.onPluginEnabled(descriptor);

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

    /**
     * Checks if a plugin is enabled based on the settings.
     * This method retrieves the enabled plugins from the settings and checks if the specified plugin is enabled.
     *
     * @param descriptor The PluginDescriptor of the plugin to check.
     * @return true if the plugin is enabled, false otherwise.
     * @throws IllegalArgumentException if the descriptor is null.
     */
    public static boolean isPluginEnabled(PluginDescriptor descriptor) {
        if (descriptor == null)
            throw new IllegalArgumentException("PluginDescriptor cannot be null");

        Map<PluginDescriptor, Boolean> enabledPlugins = SettingsHandler.getValue(Settings.ENABLED_PLUGINS);
        if (enabledPlugins == null)
            return false;

        return enabledPlugins.getOrDefault(descriptor, false);
    }

    /**
     * Checks if a plugin is enabled, forcing the check against the loaded plugins list.
     * This method does not rely on the settings and checks directly against the loaded plugins.
     *
     * @param descriptor The PluginDescriptor of the plugin to check.
     * @return true if the plugin is enabled, false otherwise.
     * @throws IllegalArgumentException if the descriptor is null.
     */
    public static boolean isPluginEnabledForce(PluginDescriptor descriptor) {
        if (descriptor == null)
            throw new IllegalArgumentException("PluginDescriptor cannot be null");

        return LOADED_PLUGINS.stream()
                .anyMatch(result ->
                        result.descriptor().equals(descriptor) &&
                                result.pluginInstance() != null &&
                                result.classLoader() != null);
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

    /**
     * Sets the enabled status of multiple plugins.
     * This method updates the settings with the provided map of PluginDescriptor to their enabled status.
     *
     * @param enabledPlugins A map of PluginDescriptor to their enabled status (true for enabled, false for disabled).
     * @throws IllegalArgumentException if the enabledPlugins map is null.
     */
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
     * Encodes a map of enabled plugins into a JSON element.
     * The JSON will be in the format: {"pluginId1": true, "pluginId2": false, ...}
     *
     * @param enabledPlugins The map of PluginDescriptor to their enabled status.
     * @return A JsonElement representing the enabled plugins.
     * @throws IllegalArgumentException if the enabledPlugins map is null.
     */
    public static JsonElement encodeEnabledPlugins(Map<PluginDescriptor, Boolean> enabledPlugins) {
        if (enabledPlugins == null)
            throw new IllegalArgumentException("Enabled plugins map cannot be null");

        var jsonObject = new JsonObject();
        for (Map.Entry<PluginDescriptor, Boolean> entry : enabledPlugins.entrySet()) {
            jsonObject.addProperty(entry.getKey().getId(), entry.getValue());
        }

        return jsonObject;
    }

    /**
     * Decodes a JSON element representing enabled plugins into a map of PluginDescriptor to their enabled status.
     * The JSON should be in the format: {"pluginId1": true, "pluginId2": false, ...}
     *
     * @param json The JSON element containing the enabled plugins.
     * @return A map of PluginDescriptor to their enabled status.
     * @throws IllegalArgumentException if the JSON is null or not an object.
     */
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

    /**
     * Loads a resource from the specified plugin's class loader.
     * The resource path should be relative to the plugin's root.
     *
     * @param descriptor   The PluginDescriptor of the plugin from which to load the resource.
     * @param resourcePath The path to the resource within the plugin.
     * @return An InputStream for the resource, or null if the resource is not found.
     * @throws IllegalArgumentException if the plugin or resource path is null or empty.
     */
    public static InputStream loadResource(PluginDescriptor descriptor, String resourcePath) {
        if (descriptor == null || resourcePath == null || resourcePath.isEmpty())
            throw new IllegalArgumentException("Plugin and resource path cannot be null or empty");

        if (!PluginManager.isPluginEnabled(descriptor)) {
            Railroad.LOGGER.warn("Plugin {} is not enabled, cannot load resource: {}", descriptor.getName(), resourcePath);
            return null;
        }

        PluginLoadResult loadResult = LOADED_PLUGINS.stream()
                .filter(result -> result.descriptor().equals(descriptor))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + descriptor.getName()));

        PluginClassLoader classLoader = loadResult.classLoader();
        if (classLoader == null)
            throw new IllegalStateException("Plugin class loader is not available for: " + descriptor.getName());

        return classLoader.getResourceAsStream("assets/" + descriptor.getId() + "/" + resourcePath);
    }

    public static List<InputStream> loadResourcesFromAllPlugins(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty())
            throw new IllegalArgumentException("Resource path cannot be null or empty");

        List<InputStream> resources = new ArrayList<>();
        for (PluginLoadResult loadResult : LOADED_PLUGINS) {
            PluginDescriptor descriptor = loadResult.descriptor();
            if (!PluginManager.isPluginEnabled(descriptor))
                continue;

            PluginClassLoader classLoader = loadResult.classLoader();
            if (classLoader != null) {
                InputStream resourceStream = classLoader.getResourceAsStream("assets/" + descriptor.getId() + "/" + resourcePath);
                if (resourceStream != null) {
                    resources.add(resourceStream);
                }
            }
        }

        return resources;
    }
}
