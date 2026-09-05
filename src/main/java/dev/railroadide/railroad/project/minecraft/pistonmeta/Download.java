package dev.railroadide.railroad.project.minecraft.pistonmeta;

import com.google.gson.JsonObject;
import dev.railroadide.railroad.Railroad;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Download location and expected integrity metadata for a Minecraft artifact.
 *
 * @param sha1 the expected SHA-1 hash
 * @param size the expected size in bytes
 * @param url the download URL
 */
public record Download(String sha1, long size, String url) {
    /**
     * Parses artifact download metadata from JSON.
     *
     * @param json the metadata object
     * @return the parsed download, or null if the JSON object is null
     */
    public static Download fromJson(JsonObject json) {
        return Railroad.GSON.fromJson(json, Download.class);
    }

    /**
     * Downloads the artifact using the last slash-separated URL segment as the file name.
     * An existing destination file is replaced; the advertised hash and size are not checked.
     *
     * @param path the destination directory
     * @return the absolute destination path
     * @throws RuntimeException if preparing the destination or downloading fails
     */
    public Path downloadToPath(Path path) {
        String[] split = this.url.split("/");
        String fileName = split[split.length - 1];

        return downloadToPath(path, fileName);
    }

    /**
     * Downloads the artifact to the specified file, creating parent directories and replacing existing contents.
     * The advertised hash and size are not checked.
     *
     * @param path the destination directory
     * @param fileName the file name to resolve against the directory
     * @return the absolute destination path
     * @throws RuntimeException if preparing the destination or downloading fails
     */
    public Path downloadToPath(Path path, String fileName) {
        Path resolved = path.toAbsolutePath().resolve(fileName);
        Railroad.LOGGER.debug("Downloading " + this.url + " to " + resolved);

        try {
            Files.createDirectories(resolved.getParent());
            Files.deleteIfExists(resolved);
        } catch (IOException exception) {
            throw new RuntimeException("Failed to create directories for " + resolved + "!", exception);
        }

        try (InputStream inputStream = new URI(this.url).toURL().openStream()) {
            Files.write(resolved, inputStream.readAllBytes());
        } catch (IOException | URISyntaxException exception) {
            throw new RuntimeException("Failed to download " + this.url + "!", exception);
        }

        Railroad.LOGGER.debug("Downloaded " + this.url + " to " + resolved + "!");
        return resolved;
    }
}
