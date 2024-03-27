package io.github.railroad.settings;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public enum SettingsCategory {
    GENERAL("General", FontAwesomeSolid.COG),
    APPEARANCE("Appearance", FontAwesomeSolid.PAINT_BRUSH, Color.BLUE),
    BEHAVIOR("Behavior", FontAwesomeSolid.COGS, Color.RED),
    KEYMAPS("Keymaps", FontAwesomeSolid.KEYBOARD, Color.GREEN),
    PLUGINS("Plugins", FontAwesomeSolid.PLUG, Color.ORANGE),
    PROJECTS("Projects", FontAwesomeSolid.FOLDER, Color.PURPLE),
    TOOLS("Tools", FontAwesomeSolid.TOOLBOX, Color.YELLOW);

    private final String name;
    private final Ikon icon;
    private final Paint color;

    SettingsCategory(String name, Ikon icon, Paint color) {
        this.name = name;
        this.icon = icon;
        this.color = color;
    }

    SettingsCategory(String name, Ikon icon) {
        this(name, icon, Color.BLACK);
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
