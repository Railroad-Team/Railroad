package dev.railroadide.railroad.switchboard.pojo;

/**
 * Metadata describing a Fabric Loader release.
 *
 * @param separator the Maven coordinate separator
 * @param build the Fabric Loader build number
 * @param maven the Maven coordinate for the loader
 * @param version the loader version
 * @param stable whether the release is stable
 */
public record FabricLoaderVersion(String separator, int build, String maven, String version, boolean stable) {
}
