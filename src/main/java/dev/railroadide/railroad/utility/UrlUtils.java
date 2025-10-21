package dev.railroadide.railroad.utility;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class UrlUtils {
    private UrlUtils() {
        // Prevent instantiation
    }

    /**
     * Checks if a URL exists by attempting to open a connection and checking the content type.
     *
     * @param url the URL to check
     * @return true if the URL exists, false otherwise
     */
    public static boolean urlExists(String url) {
        try {
            return new URI(url).toURL().openConnection().getContentType() != null;
        } catch (IOException | URISyntaxException exception) {
            return false;
        }
    }

    /**
     * Checks if a URL returns a 404 status code.
     *
     * @param url the URL to check
     * @return true if the URL returns a 404 status code, false otherwise
     */
    public static boolean is404(String url) {
        return getResponseCode(url) == 404;
    }

    /**
     * Gets the HTTP response code for a given URL.
     *
     * @param url the URL to check
     * @return the HTTP response code, or -1 if an error occurs
     */
    public static int getResponseCode(String url) {
        try {
            return getResponseCode(new URI(url));
        } catch (URISyntaxException exception) {
            return -1;
        }
    }

    /**
     * Gets the HTTP response code for a given URI.
     *
     * @param uri the URI to check
     * @return the HTTP response code, or -1 if an error occurs
     */
    public static int getResponseCode(URI uri) {
        try {
            return getResponseCode(uri.toURL());
        } catch (IOException exception) {
            return -1;
        }
    }

    /**
     * Gets the HTTP response code for a given URL.
     *
     * @param url the URL to check
     * @return the HTTP response code, or -1 if an error occurs
     */
    public static int getResponseCode(URL url) {
        try {
            return ((HttpURLConnection) url.openConnection()).getResponseCode();
        } catch (IOException exception) {
            return -1;
        }
    }

    public static void writeBody(String url, Path path) {
        try (var in = new URI(url).toURL().openStream()) {
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to write body from URL: " + url + " to path: " + path, exception);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid URL: " + url, exception);
        }
    }
}
