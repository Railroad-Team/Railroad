package dev.railroadide.core.ui;

import dev.railroadide.core.ui.localized.LocalizedMenu;
import dev.railroadide.core.ui.localized.LocalizedMenuItem;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SeparatorMenuItem;

public class RRMenuBar extends MenuBar {
    /**
     * Creates a new RRMenuBar with the specified child menus.
     *
     * @param children The initial menus to add to the menu bar.
     */
    public RRMenuBar(Menu... children) {
        super(children);
    }

    /**
     * Creates a new RRMenuBar, optionally adding default menus such as Help.
     *
     * @param addDefaults If true, adds default menus like Help with links to documentation, issues, and Discord.
     * @param children    The initial menus to add to the menu bar.
     */
    public RRMenuBar(boolean addDefaults, Menu... children) {
        this(children);

        if (addDefaults) {
            var help = new LocalizedMenu("railroad.menu.help");
            help.getItems().add(new LocalizedMenuItem("railroad.menu.help.documentation", "https://railroadide.dev/"));
            help.getItems().add(new SeparatorMenuItem());
            help.getItems().add(new LocalizedMenuItem("railroad.menu.help.issues", "https://github.com/Railroad-Team/Railroad/issues"));
            help.getItems().add(new LocalizedMenuItem("railroad.menu.help.discord", "https://discord.turtywurty.dev/"));

            getMenus().addLast(help);
        }
    }
}
