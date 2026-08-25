package dev.railroadide.railroad.utility;

import dev.railroadide.railroad.Railroad;

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
            var uri = new URI(url);
            Railroad.getHostServicess().showDocument(uri.toString());
        } catch (URISyntaxException exception) {
            throw new RuntimeException("Failed to open URL: " + url, exception);
        }
    }
}
