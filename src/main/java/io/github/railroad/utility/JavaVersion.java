package io.github.railroad.utility;

import io.github.railroad.Railroad;
import io.github.railroad.project.facet.data.JavaFacetData;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public record JavaVersion(int major, int minor) implements Comparable<JavaVersion> {
    public static JavaVersion fromMajor(int major) {
        return new JavaVersion(major, 0);
    }

    public static JavaVersion fromMajorMinor(int major, int minor) {
        return new JavaVersion(major, minor);
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

    public String toReleaseString() {
        if (major < 45) {
            return major + "." + minor;
        }

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
                classMajor = Integer.parseInt(trimmed) + 44;
            }
        } catch (NumberFormatException exception) {
            Railroad.LOGGER.warn("Invalid Java release format: {}", release, exception);
            return fromMajor(-1); // Invalid format
        }

        return fromMajorMinor(classMajor, classMinor);
    }
}
