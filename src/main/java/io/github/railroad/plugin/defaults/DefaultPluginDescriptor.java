package io.github.railroad.plugin.defaults;

import io.github.railroad.railroadpluginapi.PluginDescriptor;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

/**
 * Default implementation of the PluginDescriptor interface.
 * This class provides a builder pattern for creating plugin descriptors.
 */
@Getter
public class DefaultPluginDescriptor implements PluginDescriptor {
    private final String id;
    private final String name;
    private final String version;
    private final String author;
    private final String description;
    private final String website;
    private final String license;
    private final String iconPath;
    private final String mainClass;
    private final Map<String, String> dependencies;

    /**
     * Constructs a DefaultPluginDescriptor with the provided parameters.
     *
     * @param id          The unique identifier of the plugin.
     * @param name        The name of the plugin.
     * @param version     The version of the plugin.
     * @param author      The author of the plugin.
     * @param description A brief description of the plugin.
     * @param website     The website URL for the plugin.
     * @param license     The license under which the plugin is distributed.
     * @param iconPath    The path to the plugin's icon.
     * @param mainClass   The main class of the plugin.
     * @param dependencies A map of dependencies required by the plugin.
     */
    public DefaultPluginDescriptor(String id, String name, String version, String author, String description,
                                   String website, String license, String iconPath, String mainClass,
                                   Map<String, String> dependencies) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.author = author;
        this.description = description;
        this.website = website;
        this.license = license;
        this.iconPath = iconPath;
        this.mainClass = mainClass;
        this.dependencies = dependencies;
    }

    /**
     * Creates a new Builder instance for constructing DefaultPluginDescriptor.
     *
     * @param id The unique identifier of the plugin.
     * @return A new Builder instance.
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Builder class for constructing DefaultPluginDescriptor instances.
     */
    public static class Builder {
        private final String id;
        private String name;
        private String version;
        private String author;
        private String description;
        private String website;
        private String license;
        private String iconPath;
        private String mainClass;
        private Map<String, String> dependencies;

        /**
         * Constructs a Builder with the required plugin ID.
         *
         * @param id The unique identifier of the plugin.
         */
        public Builder(String id) {
            this.id = id;
        }

        /**
         * Sets the name of the plugin.
         *
         * @param name The name of the plugin.
         * @return The Builder instance for method chaining.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the version of the plugin.
         *
         * @param version The version of the plugin.
         * @return The Builder instance for method chaining.
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the author of the plugin.
         *
         * @param author The author of the plugin.
         * @return The Builder instance for method chaining.
         */
        public Builder author(String author) {
            this.author = author;
            return this;
        }

        /**
         * Sets the description of the plugin.
         *
         * @param description A brief description of the plugin.
         * @return The Builder instance for method chaining.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the website URL for the plugin.
         *
         * @param website The website URL for the plugin.
         * @return The Builder instance for method chaining.
         */
        public Builder website(String website) {
            this.website = website;
            return this;
        }

        /**
         * Sets the license under which the plugin is distributed.
         *
         * @param license The license of the plugin.
         * @return The Builder instance for method chaining.
         */
        public Builder license(String license) {
            this.license = license;
            return this;
        }

        /**
         * Sets the path to the plugin's icon.
         *
         * @param iconPath The path to the plugin's icon.
         * @return The Builder instance for method chaining.
         */
        public Builder iconPath(String iconPath) {
            this.iconPath = iconPath;
            return this;
        }

        /**
         * Sets the main class of the plugin.
         *
         * @param mainClass The main class of the plugin.
         * @return The Builder instance for method chaining.
         */
        public Builder mainClass(String mainClass) {
            this.mainClass = mainClass;
            return this;
        }

        /**
         * Sets the dependencies required by the plugin.
         *
         * @param dependencies A map of dependencies required by the plugin.
         * @return The Builder instance for method chaining.
         */
        public Builder dependencies(Map<String, String> dependencies) {
            this.dependencies = dependencies;
            return this;
        }

        /**
         * Adds a single dependency to the plugin descriptor.
         *
         * @param key   The key of the dependency.
         * @param value The value of the dependency.
         * @return The Builder instance for method chaining.
         */
        public Builder dependency(String key, String value) {
            if (this.dependencies == null) {
                this.dependencies = new HashMap<>();
            }

            this.dependencies.put(key, value);
            return this;
        }

        /**
         * Builds and returns a DefaultPluginDescriptor instance with the provided parameters.
         *
         * @return A new DefaultPluginDescriptor instance.
         */
        public DefaultPluginDescriptor build() {
            return new DefaultPluginDescriptor(id, name, version, author, description, website, license, iconPath,
                    mainClass, dependencies);
        }
    }
}
