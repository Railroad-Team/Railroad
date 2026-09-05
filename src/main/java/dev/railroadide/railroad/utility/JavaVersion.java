package dev.railroadide.railroad.utility;

import dev.railroadide.railroad.Railroad;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Represents a Java version with major and minor components.
 * Provides methods to create instances from major/minor values or release strings.
 *
 * @param major The major version number.
 * @param minor The minor version number.
 */
public record JavaVersion(int major, int minor) implements Comparable<JavaVersion> {
    /**
     * Creates a JavaVersion instance from a major version number.
     *
     * @param major The major version number.
     * @return A new JavaVersion instance with the specified major version and a minor version of 0.
     */
    public static JavaVersion fromMajor(int major) {
        return new JavaVersion(major, 0);
    }

    /**
     * Creates a JavaVersion instance from major and minor version numbers.
     *
     * @param major The major version number.
     * @param minor The minor version number.
     * @return A new JavaVersion instance with the specified major and minor versions.
     */
    public static JavaVersion fromMajorMinor(int major, int minor) {
        return new JavaVersion(major, minor);
    }

    /**
     * Creates a JavaVersion instance from a release string.
     * The release string can be in the format "1.x", "x", or "x.y" where x and y are integers.
     * It also handles preview releases indicated by "(preview)".
     *
     * @param release The release string representing the Java version.
     * @return A new JavaVersion instance corresponding to the release string.
     */
    public static JavaVersion fromReleaseString(String release) {
        if (release == null || release.isEmpty()) {
            Railroad.LOGGER.warn("Invalid Java release string: {}", release);
            return fromMajor(-1);
        }

        String trimmed = release.trim().toLowerCase(Locale.ROOT);
        boolean preview = false;
        if (trimmed.endsWith("(preview)")) {
            preview = true;
            trimmed = trimmed.substring(0, trimmed.length() - "(preview)".length()).trim();
        }

        int classMajor;
        int classMinor = preview ? 1 : 0; // Default minor version for non-preview releases

        try {
            if (trimmed.startsWith("1.")) { // Legacy Java versions (1.x)
                int legacy = Integer.parseInt(trimmed.substring(2));
                classMajor = legacy + 44;
            } else { // Modern Java versions (9 and above)
                String[] dotSplot = trimmed.split("\\.");
                if (dotSplot.length > 1) {
                    classMajor = Integer.parseInt(dotSplot[0]);
                    classMinor = Integer.parseInt(dotSplot[1]);
                } else {
                    classMajor = Integer.parseInt(trimmed) + 44;
                }
            }
        } catch (NumberFormatException exception) {
            Railroad.LOGGER.warn("Invalid Java release format: {}", release, exception);
            return fromMajor(-1); // Invalid format
        }

        return fromMajorMinor(classMajor, classMinor);
    }

    @Override
    public int compareTo(JavaVersion o) {
        int cmp = Integer.compare(this.major, o.major);
        return (cmp != 0) ? cmp : Integer.compare(this.minor, o.minor);
    }

    @Override
    public @NotNull String toString() {
        return major + "." + minor;
    }

    /**
     * Converts the JavaVersion instance to a release string representation.
     * For major versions less than 45, it returns "major.minor".
     * For major versions 45 and above, it returns "1.x" for legacy versions or "x" for modern versions,
     * appending "(preview)" if the minor version is non-zero.
     *
     * @return A string representation of the Java version in release format.
     */
    public String toReleaseString() {
        if (major < 45)
            return major + "." + minor;

        int releaseNumber = major - 44;
        String base;
        if (major <= 48) {
            base = "1." + releaseNumber;
        } else {
            base = Integer.toString(releaseNumber);
        }

        if (minor != 0) {
            base += "(preview)";
        }

        return base;
    }
}
