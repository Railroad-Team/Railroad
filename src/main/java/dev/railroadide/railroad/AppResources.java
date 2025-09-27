package dev.railroadide.railroad;


import javafx.scene.image.Image;

import java.io.InputStream;

public class AppResources {
    private static final String DEFAULT_ICON_PATH = "images/logo.png";

    public static InputStream iconStream() {
        return Railroad.getResourceAsStream(DEFAULT_ICON_PATH);
    }

    public static Image icon() {
        return new Image(iconStream());
    }
}
