package dev.railroadide.railroad.welcome.project;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.welcome.project.ui.details.FabricProjectDetailsPane;
import dev.railroadide.railroad.welcome.project.ui.details.ForgeProjectDetailsPane;
import dev.railroadide.railroad.welcome.project.ui.details.NeoforgeProjectDetailsPane;
import javafx.scene.Node;
import javafx.scene.image.Image;
import lombok.Getter;

import java.util.function.Supplier;

@Getter
public enum ProjectType {
    FABRIC("Fabric", new Image(Railroad.getResourceAsStream("images/fabric.png")), FabricProjectDetailsPane::new),
    FORGE("Forge", new Image(Railroad.getResourceAsStream("images/forge.png")), ForgeProjectDetailsPane::new),
    NEOFORGED("NeoForged", new Image(Railroad.getResourceAsStream("images/neoforged.png")), NeoforgeProjectDetailsPane::new);

    private final String name;
    private final Image icon;
    private final Supplier<? extends Node> detailsPaneSupplier;

    ProjectType(String name, Image icon, Supplier<? extends Node> detailsPaneSupplier) {
        this.name = name;
        this.icon = icon;
        this.detailsPaneSupplier = detailsPaneSupplier;
    }

    public Node createDetailsPane() {
        return detailsPaneSupplier.get();
    }
}
