package io.github.railroad.utility;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileHandler {
    public static String getExtension(String path) {
        int i = path.lastIndexOf('.');
        if (i > 0) {
            return path.substring(i + 1);
        }

        return null;
    }

    public static void copyUrlToFile(String url, Path path) {
        try (InputStream in = new URI(url).toURL().openStream()) {
            Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | URISyntaxException exception) {
            throw new RuntimeException("Failed to copy URL to file", exception);
        }
    }
}
