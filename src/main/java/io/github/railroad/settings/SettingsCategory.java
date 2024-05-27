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

    private final String key;
    private final Ikon icon;
    private final Paint color;

    SettingsCategory(String key, Ikon icon, Paint color) {
        this.key = key;
        this.icon = icon;
        this.color = color;
    }

    SettingsCategory(String key, Ikon icon) {
        this(key, icon, Color.WHITE);
    }

    public String getName() {
        return key;
    }

    public Ikon getIcon() {
        return icon;
    }

    public Paint getColor() {
        return color;
    }
}
