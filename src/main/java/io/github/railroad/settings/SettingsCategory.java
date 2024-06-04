package io.github.railroad.settings;

import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

public enum SettingsCategory {
    GENERAL("railroad.home.settings.general", FontAwesomeSolid.COG),
    APPEARANCE("railroad.home.settings.appearance", FontAwesomeSolid.PAINT_BRUSH),
    BEHAVIOR("railroad.home.settings.behavior", FontAwesomeSolid.COGS),
    KEYMAPS("railroad.home.settings.keymaps", FontAwesomeSolid.KEYBOARD),
    PLUGINS("railroad.home.settings.plugins", FontAwesomeSolid.PLUG),
    PROJECTS("railroad.home.settings.projects", FontAwesomeSolid.FOLDER),
    TOOLS("railroad.home.settings.tools", FontAwesomeSolid.TOOLBOX);

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

    public String getKey() {
        return key;
    }

    public Ikon getIcon() {
        return icon;
    }

    public Paint getColor() {
        return color;
    }

    public static SettingsCategory fromName(String name) {
        SettingsCategory res = null;
        for(SettingsCategory settingsCategory : values()) {
            if(settingsCategory.name().equalsIgnoreCase(name)) {
                res = settingsCategory;
                break;
            }
        }

        return res;
    }
}