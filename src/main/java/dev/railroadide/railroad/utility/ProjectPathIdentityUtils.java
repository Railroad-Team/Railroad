package dev.railroadide.railroad.utility;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Utility class for normalizing and comparing project paths.
 */
public final class ProjectPathIdentityUtils {
    private ProjectPathIdentityUtils() {
    }

    /**
     * Normalizes the given path to its absolute and real path.
     *
     * @param path the path to normalize
     * @return the normalized path
     */
    public static Path normalize(Path path) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        try {
            return normalized.toRealPath();
        } catch (IOException _) {
            return normalized;
        }
    }

    /**
     * Generates a key for the given path, which is used for comparison.
     * On Windows, the key is converted to lowercase to ensure case-insensitive comparison.
     *
     * @param path the path to generate a key for
     * @return the generated key
     */
    public static String key(Path path) {
        String key = normalize(path).toString();
        return OperatingSystem.isWindows() ? key.toLowerCase(Locale.ROOT) : key;
    }

    /**
     * Compares two paths for equality based on their normalized keys.
     *
     * @param first the first path to compare
     * @param second the second path to compare
     * @return true if the paths are considered equal, false otherwise
     */
    public static boolean matches(Path first, Path second) {
        return key(first).equals(key(second));
    }
}
