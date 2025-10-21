package dev.railroadide.core.switchboard.pojo;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public record MinecraftVersion(
    String id,
    String type,
    String url,
    LocalDateTime releaseTime,
    LocalDateTime time
) implements Comparable<MinecraftVersion> {
    public static MinecraftVersion determineDefaultMinecraftVersion(List<MinecraftVersion> versions) {
        if (versions == null || versions.isEmpty())
            return null;

        return versions.stream()
            .filter(version -> version != null && version.getType() == Type.RELEASE)
            .findFirst()
            .orElseGet(versions::getFirst);
    }

    public Type getType() {
        return Type.fromString(type);
    }

    @Override
    public int compareTo(@NotNull MinecraftVersion other) {
        return this.releaseTime.compareTo(other.releaseTime);
    }

    public enum Type {
        RELEASE,
        SNAPSHOT,
        OLD_ALPHA,
        OLD_BETA;

        public static Type fromString(String type) {
            return switch (type.toLowerCase(Locale.ROOT)) {
                case "release" -> RELEASE;
                case "snapshot" -> SNAPSHOT;
                case "old_alpha" -> OLD_ALPHA;
                case "old_beta" -> OLD_BETA;
                default -> throw new IllegalArgumentException("Unknown Minecraft version type: " + type);
            };
        }
    }
}
