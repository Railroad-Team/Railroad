package dev.railroadide.railroad.plugin.spi;

/**
 * Railroad Plugin API
 * <p>
 * This interface defines the basic structure of a plugin in the Railroad system.
 * Plugins can be enabled and disabled, allowing for dynamic behavior
 * within the Railroad application.
 */
public interface Plugin {
    /**
     * Enables the plugin, initializing resources and starting any necessary processes.
     *
     * @param context The context in which the plugin operates, providing access to services and resources.
     */
    void onEnable(PluginContext context);

    /**
     * Disables the plugin, cleaning up resources and stopping any ongoing processes.
     *
     * @param context The context in which the plugin operates, providing access to services and resources.
     */
    void onDisable(PluginContext context);
}
