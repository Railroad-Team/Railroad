package dev.railroadide.railroad.project.minecraft;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

public record MinecraftVersion(String id, VersionType type, String url, LocalDateTime time,
                               LocalDateTime releaseTime) implements Comparable<MinecraftVersion> {
    public boolean isRelease() {
        return type == VersionType.RELEASE;
    }

    @Override
    public int compareTo(@NotNull MinecraftVersion other) {
        return this.releaseTime.compareTo(other.releaseTime);
    }

    public enum VersionType {
        RELEASE,
        SNAPSHOT,
        OLD_BETA,
        OLD_ALPHA;

        public static Optional<VersionType> fromString(String type) {
            return Optional.ofNullable(switch (type.toLowerCase(Locale.ROOT)) {
                case "release" -> RELEASE;
                case "snapshot" -> SNAPSHOT;
                case "old_beta" -> OLD_BETA;
                case "old_alpha" -> OLD_ALPHA;
                default -> null;
            });
        }
    }
}
