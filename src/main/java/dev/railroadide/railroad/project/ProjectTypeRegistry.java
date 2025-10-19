package dev.railroadide.railroad.project;

import dev.railroadide.core.project.ProjectType;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.onboarding.impl.FabricProjectOnboarding;
import dev.railroadide.railroad.project.onboarding.impl.ForgeProjectOnboarding;
import dev.railroadide.railroad.project.onboarding.impl.NeoforgeProjectOnboarding;
import dev.railroadide.railroad.project.onboarding.ui.OnboardingProjectCreationPane;
import javafx.scene.image.Image;

public class ProjectTypeRegistry {
    public static final ProjectType FABRIC = ProjectType.REGISTRY.register("railroad:fabric",
        ProjectType.builder()
            .name("Fabric")
            .description("railroad.project.type.fabric.description")
            .icon(new Image(Railroad.getResourceAsStream("images/fabric.png")))
            .onboardingUI(() -> new OnboardingProjectCreationPane(scene -> new FabricProjectOnboarding().start(scene)))
            .build());

    public static final ProjectType FORGE = ProjectType.REGISTRY.register("railroad:forge",
        ProjectType.builder()
            .name("Forge")
            .description("railroad.project.type.forge.description")
            .icon(new Image(Railroad.getResourceAsStream("images/forge.png")))
            .onboardingUI(() -> new OnboardingProjectCreationPane(scene -> new ForgeProjectOnboarding().start(scene)))
            .build());

    public static final ProjectType NEOFORGE = ProjectType.REGISTRY.register("railroad:neoforge",
        ProjectType.builder()
            .name("Neoforge")
            .description("railroad.project.type.neoforge.description")
            .icon(new Image(Railroad.getResourceAsStream("images/neoforge.png")))
            .onboardingUI(() -> new OnboardingProjectCreationPane(scene -> new NeoforgeProjectOnboarding().start(scene)))
            .build());

    public static void initialize() {
        // Intentionally left blank
    }
}
