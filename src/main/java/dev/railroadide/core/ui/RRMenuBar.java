package dev.railroadide.core.ui;

import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.SeparatorMenuItem;

public class RRMenuBar extends MenuBar {
    public RRMenuBar(Menu... children) {
        super(children);
    }

    public RRMenuBar(boolean addDefaults, Menu... children) {
        this(children);
        if (addDefaults) {
            var help = new Menu("Help");
            help.getItems().add(new RRMenuItem("Documentation", "https://railroadide.dev/"));
            help.getItems().add(new SeparatorMenuItem());
            help.getItems().add(new RRMenuItem("Issues", "https://github.com/Railroad-Team/Railroad/issues"));
            help.getItems().add(new RRMenuItem("Discord", "https://railroadide.dev/discord"));

            this.getMenus().addLast(help);
        }
    }
}
