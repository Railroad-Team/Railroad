package dev.railroadide.railroad.switchboard.pojo;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Metadata describing a Minecraft version.
 *
 * @param id the version identifier
 * @param type the serialized version type
 * @param url the URL of the version metadata
 * @param releaseTime the release timestamp
 * @param time the timestamp at which the metadata was published
 */
public record MinecraftVersion(
    String id,
    String type,
    String url,
    LocalDateTime releaseTime,
    LocalDateTime time
) implements Comparable<MinecraftVersion> {
    /**
     * Selects the first release version, or the first supplied version when no release exists.
     *
     * @param versions the versions from which to choose a default
     * @return the preferred default version, or {@code null} when the input is empty
     */
    public static MinecraftVersion determineDefaultMinecraftVersion(List<MinecraftVersion> versions) {
        if (versions == null || versions.isEmpty())
            return null;

        return versions.stream()
            .filter(version -> version != null && version.getType() == Type.RELEASE)
            .findFirst()
            .orElseGet(versions::getFirst);
    }

    /**
     * Converts the serialized type into the corresponding enum value.
     *
     * @return this version's type
     */
    public Type getType() {
        return Type.fromString(type);
    }

    /**
     * Orders versions by release timestamp.
     *
     * @param other the version to compare with
     * @return a negative value, zero, or a positive value as this version is older than,
     *         equal to, or newer than {@code other}
     */
    @Override
    public int compareTo(@NotNull MinecraftVersion other) {
        return this.releaseTime.compareTo(other.releaseTime);
    }

    /** The categories of Minecraft versions exposed by Switchboard. */
    public enum Type {
        /** A stable Minecraft release. */
        RELEASE,
        /** A development snapshot. */
        SNAPSHOT,
        /** An old alpha release. */
        OLD_ALPHA,
        /** An old beta release. */
        OLD_BETA;

        /**
         * Parses a serialized Minecraft version type.
         *
         * @param type the case-insensitive serialized type
         * @return the matching version type
         * @throws IllegalArgumentException if {@code type} is unknown
         */
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
