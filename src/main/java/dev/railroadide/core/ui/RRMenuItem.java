package dev.railroadide.core.ui;

import dev.railroadide.core.utility.DesktopUtils;
import javafx.scene.control.MenuItem;

public class RRMenuItem extends MenuItem {
    public RRMenuItem(String text) {
        super(text);
    }

    public RRMenuItem(String text, String url) {
        this(text);
        this.setOnAction(event -> {
            DesktopUtils.openUrl(url);
        });
    }
}
