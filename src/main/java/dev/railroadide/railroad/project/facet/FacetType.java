package dev.railroadide.railroad.project.facet;

/**
 * Describes the type of a project facet, including its unique ID, display name, description, icon, and associated data
 * class.
 * Used to distinguish between different kinds of facets (e.g., Java, Gradle, Fabric).
 *
 * @param <D> the type of data associated with this facet type
 * @param id the unique, nonblank facet identifier
 * @param name the nonblank display name
 * @param description the description, normalized to an empty string when null
 * @param iconPath the optional icon resource path, which must not be blank when provided
 * @param dataClass the class used to deserialize the facet data
 */
public record FacetType<D>(
    String id,
    String name,
    String description,
    String iconPath,
    Class<D> dataClass
) {
    /**
     * Validates the facet metadata and supplies an empty description when none is provided.
     *
     * @param id the unique facet identifier
     * @param name the display name
     * @param description the description, or null for an empty description
     * @param iconPath the icon resource path, or null for no icon
     * @param dataClass the facet data class
     * @throws IllegalArgumentException if a required value is null or an identifier, name, or supplied icon path is blank
     */
    public FacetType {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Facet ID cannot be null or blank");

        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Facet name cannot be null or blank");

        if (description == null) {
            description = "";
        }

        if (iconPath != null && iconPath.isBlank())
            throw new IllegalArgumentException("Icon path cannot be blank if provided");

        if (dataClass == null)
            throw new IllegalArgumentException("Data class cannot be null");
    }

    /**
     * Builder for creating {@link FacetType} instances with custom properties.
     *
     * @param <D> the type of data associated with the facet type
     */
    public static class Builder<D> {
        private final String id;
        private final Class<D> dataClass;
        private String name = "Untitled Facet";
        private String description = "";
        private String iconPath = null;

        /**
         * Starts a builder with the default name, an empty description, and no icon.
         *
         * @param id the facet identifier, validated when built
         * @param dataClass the facet data class, validated when built
         */
        public Builder(String id, Class<D> dataClass) {
            this.id = id;
            this.dataClass = dataClass;
        }

        /**
         * Sets the facet display name.
         *
         * @param name the nonblank display name
         * @return this builder
         */
        public Builder<D> name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the facet description.
         *
         * @param description the description, or null for an empty description
         * @return this builder
         */
        public Builder<D> description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the optional icon resource path.
         *
         * @param iconPath the nonblank resource path, or null for no icon
         * @return this builder
         */
        public Builder<D> iconPath(String iconPath) {
            this.iconPath = iconPath;
            return this;
        }

        /**
         * Creates a facet type from the configured values.
         *
         * @return the validated facet type
         * @throws IllegalArgumentException if the configured values violate the facet type requirements
         */
        public FacetType<D> build() {
            return new FacetType<>(id, name, description, iconPath, dataClass);
        }
    }
}
