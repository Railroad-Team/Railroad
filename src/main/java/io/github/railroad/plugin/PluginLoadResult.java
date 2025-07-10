package io.github.railroad.plugin;

import io.github.railroad.railroadpluginapi.Plugin;
import io.github.railroad.railroadpluginapi.PluginDescriptor;

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

    public Path pluginPath() {
        return pluginPath;
    }

    public PluginDescriptor descriptor() {
        return descriptor;
    }

    public Plugin pluginInstance() {
        return pluginInstance;
    }

    public PluginClassLoader classLoader() {
        return classLoader;
    }

    public void setPlugin(Plugin plugin, PluginClassLoader classLoader) {
        this.pluginInstance = plugin;
        this.classLoader = classLoader;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (PluginLoadResult) obj;
        return Objects.equals(this.pluginPath, that.pluginPath) &&
                Objects.equals(this.descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pluginPath, descriptor);
    }

    @Override
    public String toString() {
        return "PluginLoadResult[" +
                "pluginPath=" + pluginPath + ", " +
                "descriptor=" + descriptor + ']';
    }

}
