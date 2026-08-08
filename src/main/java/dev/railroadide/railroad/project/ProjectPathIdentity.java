package dev.railroadide.railroad.project;

import dev.railroadide.railroad.utility.OperatingSystem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class ProjectPathIdentity {
    private ProjectPathIdentity() {
    }

    public static Path normalize(Path path) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        try {
            return normalized.toRealPath();
        } catch (IOException ignored) {
            return normalized;
        }
    }

    public static String key(Path path) {
        String key = normalize(path).toString();
        return OperatingSystem.isWindows() ? key.toLowerCase(Locale.ROOT) : key;
    }

    public static boolean matches(Path first, Path second) {
        return key(first).equals(key(second));
    }
}
