package dev.railroadide.railroad.plugin;

import dev.railroadide.railroadpluginapi.Plugin;
import dev.railroadide.railroadpluginapi.PluginDescriptor;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents the result of loading a plugin, containing the plugin instance and its descriptor.
 */
public final class PluginLoadResult {
    private final Path pluginPath;
    private final PluginDescriptor descriptor;
    private Plugin pluginInstance;
    private PluginClassLoader classLoader;

    /**
     * @param pluginPath The path to the loaded plugin JAR file.
     * @param descriptor The descriptor of the loaded plugin.
     */
    public PluginLoadResult(Path pluginPath, PluginDescriptor descriptor) {
        this.pluginPath = pluginPath;
        this.descriptor = descriptor;
    }

    /**
     * @return The path to the loaded plugin JAR file.
     */
    public Path pluginPath() {
        return pluginPath;
    }

    /**
     * @return The descriptor of the loaded plugin.
     */
    public PluginDescriptor descriptor() {
        return descriptor;
    }

    /**
     * @return The instance of the loaded plugin, or null if not set.
     */
    public Plugin pluginInstance() {
        return pluginInstance;
    }

    /**
     * @return The class loader used to load the plugin, or null if not set.
     */
    public PluginClassLoader classLoader() {
        return classLoader;
    }

    /**
     * Sets the plugin instance and its class loader.
     *
     * @param plugin      The plugin instance to set.
     * @param classLoader The class loader used to load the plugin.
     */
    public void setPlugin(Plugin plugin, PluginClassLoader classLoader) {
        this.pluginInstance = plugin;
        this.classLoader = classLoader;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PluginLoadResult that = (PluginLoadResult) o;
        return Objects.equals(pluginPath, that.pluginPath) && Objects.equals(descriptor, that.descriptor) && Objects.equals(pluginInstance, that.pluginInstance) && Objects.equals(classLoader, that.classLoader);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pluginPath, descriptor, pluginInstance, classLoader);
    }

    @Override
    public String toString() {
        return "PluginLoadResult{" +
                "pluginPath=" + pluginPath +
                ", descriptor=" + descriptor +
                ", pluginInstance=" + pluginInstance +
                ", classLoader=" + classLoader +
                '}';
    }
}
