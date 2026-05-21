package dev.railroadide.railroad.plugin;

import dev.railroadide.railroad.plugin.spi.Plugin;
import dev.railroadide.railroad.plugin.spi.PluginDescriptor;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Represents the result of loading a plugin, containing the plugin instance and its descriptor.
 */
public final class PluginLoadResult {
    private final Path pluginPath;
    private final PluginDescriptor descriptor;
    private Plugin pluginInstance;
    private PluginClassLoader classLoader;
    private List<String> javaInspectionRuleProviderRegistrationIds = List.of();

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
     * @return Registry ids auto-registered for this plugin's Java inspection rule providers.
     */
    public List<String> javaInspectionRuleProviderRegistrationIds() {
        return javaInspectionRuleProviderRegistrationIds;
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

    /**
     * Replaces the tracked Java inspection rule provider registration ids for this plugin.
     */
    public void setJavaInspectionRuleProviderRegistrationIds(Collection<String> registrationIds) {
        if (registrationIds == null || registrationIds.isEmpty()) {
            this.javaInspectionRuleProviderRegistrationIds = List.of();
            return;
        }

        this.javaInspectionRuleProviderRegistrationIds = List.copyOf(registrationIds);
    }

    /**
     * Clears the tracked Java inspection rule provider registration ids for this plugin.
     */
    public void clearJavaInspectionRuleProviderRegistrationIds() {
        this.javaInspectionRuleProviderRegistrationIds = List.of();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PluginLoadResult that = (PluginLoadResult) o;
        return Objects.equals(pluginPath, that.pluginPath)
                && Objects.equals(descriptor, that.descriptor)
                && Objects.equals(pluginInstance, that.pluginInstance)
                && Objects.equals(classLoader, that.classLoader)
                && Objects.equals(javaInspectionRuleProviderRegistrationIds, that.javaInspectionRuleProviderRegistrationIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pluginPath, descriptor, pluginInstance, classLoader, javaInspectionRuleProviderRegistrationIds);
    }

    @Override
    public String toString() {
        return "PluginLoadResult{" +
                "pluginPath=" + pluginPath +
                ", descriptor=" + descriptor +
                ", pluginInstance=" + pluginInstance +
                ", classLoader=" + classLoader +
                ", javaInspectionRuleProviderRegistrationIds=" + javaInspectionRuleProviderRegistrationIds +
                '}';
    }
}
