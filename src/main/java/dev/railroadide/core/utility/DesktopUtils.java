package dev.railroadide.core.utility;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public class DesktopUtils {
    /**
     * Open a URL in the default browser
     *
     * @param url The URL to open
     */
    public static void openUrl(String url) {
        if (!url.matches(StringUtils.URL_REGEX))
            throw new IllegalArgumentException(url + " is not a valid URL");

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (IOException | URISyntaxException exception) {
            throw new RuntimeException("Failed to open URL: " + url, exception);
        }
    }
}
