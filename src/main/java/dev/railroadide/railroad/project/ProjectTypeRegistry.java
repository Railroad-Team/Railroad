package dev.railroadide.railroad.project;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.project.onboarding.impl.FabricProjectOnboarding;
import dev.railroadide.railroad.project.onboarding.impl.ForgeProjectOnboarding;
import dev.railroadide.railroad.project.onboarding.impl.NeoforgeProjectOnboarding;
import dev.railroadide.railroad.project.onboarding.ui.OnboardingProjectCreationPane;
import javafx.scene.image.Image;

/**
 * A registry for managing different types of projects in the Railroad IDE.
 * This class registers various project types such as Fabric, Forge, and Neoforge,
 * each with its own metadata and onboarding UI.
 */
public class ProjectTypeRegistry {
    /**
     * Fabric project type with Fabric-specific onboarding.
     */
    public static final ProjectType FABRIC = ProjectType.REGISTRY.register("railroad:fabric",
        ProjectType.builder()
            .name("Fabric")
            .description("railroad.project.type.fabric.description")
            .icon(new Image(AppResources.getResourceAsStream("images/fabric.png")))
            .onboardingUI(() -> new OnboardingProjectCreationPane(scene -> new FabricProjectOnboarding().start(scene)))
            .build());

    /**
     * Forge project type with Forge-specific onboarding.
     */
    public static final ProjectType FORGE = ProjectType.REGISTRY.register("railroad:forge",
        ProjectType.builder()
            .name("Forge")
            .description("railroad.project.type.forge.description")
            .icon(new Image(AppResources.getResourceAsStream("images/forge.png")))
            .onboardingUI(() -> new OnboardingProjectCreationPane(scene -> new ForgeProjectOnboarding().start(scene)))
            .build());

    /**
     * Neoforge project type with Neoforge-specific onboarding.
     */
    public static final ProjectType NEOFORGE = ProjectType.REGISTRY.register("railroad:neoforge",
        ProjectType.builder()
            .name("Neoforge")
            .description("railroad.project.type.neoforge.description")
            .icon(new Image(AppResources.getResourceAsStream("images/neoforge.png")))
            .onboardingUI(
                () -> new OnboardingProjectCreationPane(scene -> new NeoforgeProjectOnboarding().start(scene)))
            .build());

    /**
     * Initializes the project type registry.
     * This method is intentionally left blank as the static initializers handle registration.
     */
    public static void initialize() {
        // Intentionally left blank
    }
}
