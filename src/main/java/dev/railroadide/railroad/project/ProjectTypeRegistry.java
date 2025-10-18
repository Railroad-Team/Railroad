package dev.railroadide.railroad.project;

import dev.railroadide.core.project.ProjectType;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.details.deprecated.FabricProjectDetailsPane;
import dev.railroadide.railroad.project.details.deprecated.ForgeProjectDetailsPane;
import dev.railroadide.railroad.project.details.deprecated.NeoforgeProjectDetailsPane;
import javafx.scene.image.Image;

public class ProjectTypeRegistry {
    public static final ProjectType FABRIC = ProjectType.builder()
        .name("Fabric")
        .description("railroad.project.type.fabric.description")
        .icon(new Image(Railroad.getResourceAsStream("images/fabric.png")))
        .detailsPane(FabricProjectDetailsPane::new)
        .build();

    public static final ProjectType FORGE = ProjectType.builder()
        .name("Forge")
        .description("railroad.project.type.forge.description")
        .icon(new Image(Railroad.getResourceAsStream("images/forge.png")))
        .detailsPane(ForgeProjectDetailsPane::new)
        .build();

    public static final ProjectType NEOFORGE = ProjectType.builder()
        .name("Neoforge")
        .description("railroad.project.type.neoforge.description")
        .icon(new Image(Railroad.getResourceAsStream("images/neoforge.png")))
        .detailsPane(NeoforgeProjectDetailsPane::new)
        .build();
}
