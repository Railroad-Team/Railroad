package dev.railroadide.railroad;

import javafx.scene.image.Image;

import java.io.InputStream;
import java.net.URL;

/**
 * A utility class for accessing application resources.
 */
public class AppResources {
    private static final String DEFAULT_ICON_PATH = "images/logo.png";

    /**
     * Get the default application icon as an InputStream.
     *
     * @return The InputStream of the default application icon
     */
    public static InputStream iconStream() {
        return getResourceAsStream(DEFAULT_ICON_PATH);
    }

    /**
     * Get the default application icon as an Image.
     *
     * @return The Image of the default application icon
     */
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
        return Railroad.class.getClassLoader()
            .getResource("assets/railroad/" + (path.startsWith("/") ? path.substring(1) : path));
    }

    /**
     * Get a resource from the assets folder as an InputStream
     *
     * @param path The path to the resource
     * @return The InputStream of the resource
     */
    public static InputStream getResourceAsStream(String path) {
        return Railroad.class.getClassLoader()
            .getResourceAsStream("assets/railroad/" + (path.startsWith("/") ? path.substring(1) : path));
    }
}
