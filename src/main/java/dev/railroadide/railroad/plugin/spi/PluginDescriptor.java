package dev.railroadide.railroad.plugin.spi;

import dev.railroadide.railroad.plugin.spi.deps.MavenDeps;

/**
 * Represents the metadata of a plugin.
 * This interface defines the methods that must be implemented by any plugin descriptor.
 */
public interface PluginDescriptor {
    /**
     * Returns the unique identifier of the plugin.
     *
     * @return the plugin ID
     */
    String getId();

    /**
     * Returns the name of the plugin.
     *
     * @return the plugin name
     */
    String getName();

    /**
     * Returns the version of the plugin.
     *
     * @return the plugin version
     */
    String getVersion();

    /**
     * Returns the author of the plugin.
     *
     * @return the plugin author
     */
    String getAuthor();

    /**
     * Returns a brief description of the plugin.
     *
     * @return the plugin description
     */
    String getDescription();

    /**
     * Returns the website URL of the plugin.
     *
     * @return the plugin website URL
     */
    String getWebsite();

    /**
     * Returns the license of the plugin.
     *
     * @return the plugin license
     */
    String getLicense();

    /**
     * Returns the path to the icon of the plugin.
     *
     * @return the plugin icon path
     */
    String getIconPath();

    /**
     * Returns the main class of the plugin.
     *
     * @return the main class name
     */
    String getMainClass();

    /**
     * Returns the dependencies required by the plugin.
     *
     * @return the plugin dependencies
     */
    MavenDeps getDependencies();
}
