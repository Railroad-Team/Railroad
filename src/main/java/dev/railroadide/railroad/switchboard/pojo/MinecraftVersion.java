package dev.railroadide.railroad.switchboard.pojo;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Locale;

public record MinecraftVersion(
    String id,
    String type,
    String url,
    Instant releaseTime,
    Instant time
) implements Comparable<MinecraftVersion> {
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
