package dev.railroadide.core.settings;

/**
 * Represents a category for settings in the Railroad application.
 * Each category has an ID, title, description, and flags indicating whether it has a title or description.
 */
public record SettingCategory(String id, String title, String description, boolean hasTitle, boolean hasDescription) {
    /**
     * Creates a new SettingCategory with the specified ID
     *
     * @param id the unique identifier for the category, in the format "pluginId:categoryId"
     * @return a new SettingCategory instance
     */
    public static SettingCategory simple(String id) {
        return new Builder(id).build();
    }

    /**
     * Creates a new Builder for SettingCategory with the specified ID.
     *
     * @param id the unique identifier for the category, in the format "pluginId:categoryId"
     * @return a Builder instance to construct a SettingCategory
     */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /**
     * Builder class for constructing SettingCategory instances.
     * This allows for setting the title, description, and flags for title and description presence.
     */
    public static class Builder {
        private final String id;
        private String title;
        private String description;
        private boolean hasTitle = true;
        private boolean hasDescription = true;

        /**
         * Constructs a Builder with the specified ID.
         *
         * @param id the unique identifier for the category, in the format "pluginId:categoryId"
         */
        public Builder(String id) {
            this.id = id;
        }

        /**
         * Sets the title for the category.
         *
         * @param title the title of the category
         * @return this Builder instance for method chaining
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * Sets the description for the category.
         *
         * @param description the description of the category
         * @return this Builder instance for method chaining
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets hasTitle to false, indicating that the category does not have a title.
         *
         * @return this Builder instance for method chaining
         */
        public Builder noTitle() {
            return hasTitle(false);
        }

        /**
         * Sets hasDescription to false, indicating that the category does not have a description.
         *
         * @return this Builder instance for method chaining
         */
        public Builder noDescription() {
            return hasDescription(false);
        }

        /**
         * Sets whether the category has a title.
         *
         * @param hasTitle true if the category has a title, false otherwise
         * @return this Builder instance for method chaining
         */
        public Builder hasTitle(boolean hasTitle) {
            this.hasTitle = hasTitle;
            return this;
        }

        /**
         * Sets whether the category has a description.
         *
         * @param hasDescription true if the category has a description, false otherwise
         * @return this Builder instance for method chaining
         */
        public Builder hasDescription(boolean hasDescription) {
            this.hasDescription = hasDescription;
            return this;
        }

        private String buildKey() {
            String[] parts = id.split(":");
            if (parts.length != 2)
                throw new IllegalArgumentException("SettingCategory ID must be in the format 'pluginId:categoryId', got: " + id);

            return parts[0] + ".settings." + parts[1];
        }

        /**
         * Builds and returns a SettingCategory instance with the specified properties.
         * If title or description is not set, it defaults to using the key with ".title" or ".description" suffix.
         *
         * @return a new SettingCategory instance
         */
        public SettingCategory build() {
            String key = buildKey();
            if (title == null) {
                title = key + ".title";
            }

            if (description == null) {
                description = key + ".description";
            }

            return new SettingCategory(id, title, description, hasTitle, hasDescription);
        }
    }
}
