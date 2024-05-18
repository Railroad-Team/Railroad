package io.github.railroad.settings;

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
    private final FontAwesomeSolid icon;

    SettingsCategory(String name, FontAwesomeSolid icon) {
        this.name = name;
        this.icon = icon;
    }

    public String getName() {
        return name;
    }

    public FontAwesomeSolid getIcon() {
        return icon;
    }

}
