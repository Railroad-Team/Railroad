package io.github.railroad.settings;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

public enum SettingsCategory {
    GENERAL("General", FontAwesomeSolid.COG),
    APPEARANCE("Appearance", FontAwesomeSolid.PAINT_BRUSH),
    BEHAVIOR("Behavior", FontAwesomeSolid.COGS),
    KEYMAPS("Keymaps", FontAwesomeSolid.KEYBOARD),
    PLUGINS("Plugins", FontAwesomeSolid.PLUG),
    PROJECTS("Projects", FontAwesomeSolid.FOLDER),
    TOOLS("Tools", FontAwesomeSolid.TOOLBOX);

    private final String name;
    private final Ikon icon;
    private final Paint color;

    SettingsCategory(String name, Ikon icon, Paint color) {
        this.name = name;
        this.icon = icon;
        this.color = color;
    }

    SettingsCategory(String name, Ikon icon) {
        this(name, icon, Color.WHITE);
    }

    public String getName() {
        return name;
    }

    public Ikon getIcon() {
        return icon;
    }

    public Paint getColor() {
        return color;
    }
}
