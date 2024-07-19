package io.github.railroad.welcome.project;

import io.github.railroad.Railroad;
import javafx.scene.image.Image;

public enum ProjectType {
    FORGE("Forge", new Image(Railroad.getResourceAsStream("images/forge.png"))),
    FABRIC("Fabric", new Image(Railroad.getResourceAsStream("images/fabric.png")));
    //NEOFORGED("NeoForged", new Image(Railroad.getResourceAsStream("images/neoforged.png"))),
    //QUILT("Quilt", new Image(Railroad.getResourceAsStream("images/quilt.png")));

    private final String name;
    private final Image icon;

    ProjectType(String name, Image icon) {
        this.name = name;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public Image getIcon() {
        return icon;
    }
}
