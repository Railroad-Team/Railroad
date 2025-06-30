package io.github.railroad.project.facet;

public record FacetType<D>(
    String id,
    String name,
    String description,
    String iconPath,
    Class<D> dataClass) {
    public FacetType {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Facet ID cannot be null or blank");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Facet name cannot be null or blank");
        }

        if (description == null) {
            description = "";
        }

        if (iconPath != null && iconPath.isBlank()) {
            throw new IllegalArgumentException("Icon path cannot be blank if provided");
        }

        if (dataClass == null) {
            throw new IllegalArgumentException("Data class cannot be null");
        }
    }

    public static class Builder<D> {
        private final String id;
        private final Class<D> dataClass;
        private String name = "Untitled Facet";
        private String description = "";
        private String iconPath = null;

        public Builder(String id, Class<D> dataClass) {
            this.id = id;
            this.dataClass = dataClass;
        }

        public Builder<D> name(String name) {
            this.name = name;
            return this;
        }

        public Builder<D> description(String description) {
            this.description = description;
            return this;
        }

        public Builder<D> iconPath(String iconPath) {
            this.iconPath = iconPath;
            return this;
        }

        public FacetType<D> build() {
            return new FacetType<>(id, name, description, iconPath, dataClass);
        }
    }
}
