package dev.railroadide.core.project;

import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.registry.RegistryManager;
import javafx.scene.Node;
import javafx.scene.image.Image;
import lombok.Getter;

import java.util.function.Supplier;

/**
 * Represents a type of project with associated metadata and functionality.
 * This class provides a builder for creating instances and a registry for managing them.
 */
public class ProjectType {
    public static final Registry<ProjectType> REGISTRY = RegistryManager.createRegistry("railroad:project_type", ProjectType.class);

    @Getter
    private final String name;
    @Getter
    private final String description;
    @Getter
    private final Image icon;
    private final Supplier<? extends Node> onboardingUISupplier;

    /**
     * Constructs a new `ProjectType` instance.
     *
     * @param name                The name of the project type.
     * @param description         A translation key for the project type description.
     * @param icon                An icon representing the project type.
     * @param onboardingUISupplier A supplier for creating an onboarding UI for the project type.
     */
    protected ProjectType(String name, String description, Image icon, Supplier<? extends Node> onboardingUISupplier) {
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.onboardingUISupplier = onboardingUISupplier;
    }

    /**
     * Creates and returns a new onboarding UI for the project type.
     *
     * @return A `Node` representing the details pane.
     */
    public Node createOnboardingUI() {
        return onboardingUISupplier.get();
    }

    /**
     * Creates and returns a new `Builder` instance for constructing `ProjectType` objects.
     *
     * @return A new `Builder` instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder class for constructing `ProjectType` instances.
     */
    public static class Builder {
        private String name;
        private String description;
        private Image icon;
        private Supplier<? extends Node> onboardingUISupplier;

        /**
         * Sets the name of the project type.
         *
         * @param name The name of the project type.
         * @return The current `Builder` instance.
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the description translation key of the project type.
         *
         * @param description The translation key for the project type description.
         * @return The current `Builder` instance.
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * Sets the icon of the project type.
         *
         * @param icon The icon representing the project type.
         * @return The current `Builder` instance.
         */
        public Builder icon(Image icon) {
            this.icon = icon;
            return this;
        }

        /**
         * Sets the supplier for creating a details pane for the project type.
         *
         * @param supplier A supplier for creating a details pane.
         * @return The current `Builder` instance.
         */
        public Builder onboardingUI(Supplier<? extends Node> supplier) {
            this.onboardingUISupplier = supplier;
            return this;
        }

        /**
         * Builds and returns a new `ProjectType` instance.
         *
         * @return A new `ProjectType` instance.
         */
        public ProjectType build() {
            return new ProjectType(name, description, icon, onboardingUISupplier);
        }
    }
}
