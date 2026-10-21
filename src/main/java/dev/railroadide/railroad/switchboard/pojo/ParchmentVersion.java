package dev.railroadide.railroad.switchboard.pojo;

/**
 * Metadata describing a Parchment release.
 *
 * @param version the Parchment version
 * @param minecraftVersion the compatible Minecraft version
 * @param isStable whether the release is stable
 */
public record ParchmentVersion(String version, String minecraftVersion, boolean isStable) {
}
