package dev.railroadide.railroad.project;

import dev.railroadide.railroad.project.minecraft.MappingChannel;
import dev.railroadide.railroad.switchboard.pojo.MinecraftVersion;
import dev.railroadide.railroad.switchboard.pojo.ParchmentVersion;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.switchboard.SwitchboardRepositories;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Provides the built-in mapping channels supported by Railroad.
 * <p>
 * Each channel supplies the mapping versions available for a Minecraft
 * version and is registered in {@link MappingChannel#REGISTRY}.
 */
public class MappingChannelRegistry {
    /** The legacy MCP community mappings. */
    public static final MappingChannel MCP = register("mcp", MappingChannel.builder()
        .versionLister(fromRepository("railroad:switchboard/mcp", SwitchboardRepositories.MCP::getVersionsForSync)));

    /** The official mappings published by Mojang. */
    public static final MappingChannel MOJMAP = register("official", MappingChannel.builder()
        .versionLister(minecraftVersion -> {
            try {
                if (SwitchboardRepositories.MINECRAFT.getVersionSync("1.14.4").map(minecraftVersion::equals)
                    .orElse(false))
                    return Collections.emptyList();
            } catch (ExecutionException | InterruptedException exception) {
                Railroad.LOGGER.error("Failed to check Minecraft version 1.14.4 for Mojang mappings support",
                    exception);
                return Collections.emptyList();
            }

            return Collections.singletonList(minecraftVersion.id());
        }));

    /** The Yarn mappings maintained by the Fabric community. */
    public static final MappingChannel YARN = register("yarn", MappingChannel.builder()
        .versionLister(fromRepository("railroad:switchboard/yarn", SwitchboardRepositories.YARN::getVersionsForSync)));

    /** The Parchment mappings, which supplement the official Minecraft mappings. */
    public static final MappingChannel PARCHMENT = register("parchment", MappingChannel.builder()
        .versionLister(fromRepository(
            "railroad:switchboard/parchment",
            minecraftVersionId -> SwitchboardRepositories.PARCHMENT.getVersionsForSync(minecraftVersionId).stream()
                .map(ParchmentVersion::version)
                .toList())));

    /**
     * Ensures that the built-in mapping channels are initialized and registered.
     * <p>
     * Calling this method has no effect after class initialization.
     */
    public static void initialize() {
        // Intentionally left blank
    }

    /**
     * Registers a mapping channel under the specified registry identifier.
     *
     * @param id the identifier to associate with the mapping channel
     * @param channel the builder used to create the mapping channel
     * @return the registered mapping channel
     */
    public static MappingChannel register(String id, MappingChannel.Builder channel) {
        return MappingChannel.REGISTRY.register(id, channel.build(id));
    }

    /**
     * Finds the mapping channels that support the specified Minecraft version.
     *
     * @param minecraftVersion the Minecraft version to check
     * @return the registered mapping channels that support the version
     */
    public static List<MappingChannel> findValidMappingChannels(MinecraftVersion minecraftVersion) {
        return MappingChannel.REGISTRY.values().stream()
            .filter(channel -> {
                try {
                    return channel.supports(minecraftVersion);
                } catch (Exception exception) {
                    Railroad.LOGGER.error("Failed to check if mapping channel {} supports Minecraft version {}",
                        channel.id(), minecraftVersion.id(), exception);
                    return false;
                }
            })
            .toList();
    }

    private static Function<MinecraftVersion, List<String>> fromRepository(
        String registryId,
        ThrowingFunction<String, List<String>> versionFetcher
    ) {
        return minecraftVersion -> fetchVersions(registryId, minecraftVersion.id(), versionFetcher);
    }

    private static <T> List<T> fetchVersions(
        String registryId,
        String minecraftVersionId,
        ThrowingFunction<String, List<T>> fetcher
    ) {
        try {
            return fetcher.apply(minecraftVersionId);
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                "Failed to fetch versions from registry " + registryId + " for " + minecraftVersionId, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                "Interrupted while fetching versions from registry " + registryId + " for " + minecraftVersionId,
                exception);
        }
    }

    @FunctionalInterface
    private interface ThrowingFunction<T, R> {
        R apply(T input) throws ExecutionException, InterruptedException;
    }
}
