package dev.railroadide.railroad;


import javafx.scene.image.Image;

import java.io.InputStream;
import java.net.URL;

public class AppResources {
    private static final String DEFAULT_ICON_PATH = "images/logo.png";

    public static InputStream iconStream() {
        return getResourceAsStream(DEFAULT_ICON_PATH);
    }

    public static Image icon() {
        return new Image(iconStream());
    }

    /**
     * Get a resource from the assets folder
     *
     * @param path The path to the resource
     * @return The URL of the resource
     */
    public static URL getResource(String path) {
        return Railroad.class.getClassLoader().getResource("assets/railroad/" + path);
    }

    /**
     * Get a resource from the assets folder as an InputStream
     *
     * @param path The path to the resource
     * @return The InputStream of the resource
     */
    public static InputStream getResourceAsStream(String path) {
        return Railroad.class.getClassLoader().getResourceAsStream("assets/railroad/" + path);
    }
}
