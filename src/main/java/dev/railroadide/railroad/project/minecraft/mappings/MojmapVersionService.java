package dev.railroadide.railroad.project.minecraft.mappings;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.VersionService;
import dev.railroadide.railroad.project.minecraft.pistonmeta.Downloads;
import dev.railroadide.railroad.project.minecraft.pistonmeta.VersionPackage;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class MojmapVersionService extends VersionService<String> {
    public static final MojmapVersionService INSTANCE = new MojmapVersionService();

    public MojmapVersionService() {
        super("Mojmap");
    }

    public MojmapVersionService(Duration ttl) {
        super("Mojmap", ttl);
    }

    public MojmapVersionService(Duration ttl, String userAgent, HttpClient httpClient) {
        super("Mojmap", ttl, userAgent, httpClient);
    }

    public MojmapVersionService(Duration ttl, String userAgent, Duration httpTimeout) {
        super("Mojmap", ttl, userAgent, httpTimeout);
    }

    @Override
    public Optional<String> latestFor(MinecraftVersion minecraftVersion) {
        return latestFor(minecraftVersion, false);
    }

    @Override
    public Optional<String> latestFor(MinecraftVersion minecraftVersion, boolean includePrereleases) {
        try {
            VersionPackage versionPackage = minecraftVersion.requestPistonMeta().get();
            Downloads downloads = versionPackage.downloads();
            return downloads.clientMappings() != null || downloads.serverMappings() != null
                ? Optional.ofNullable(minecraftVersion.id()) : Optional.empty();
        } catch (InterruptedException | ExecutionException exception) {
            Railroad.LOGGER.error("Failed to fetch Mojmap version for Minecraft version {}", minecraftVersion.id(), exception);
            return Optional.empty();
        }
    }

    @Override
    public List<String> listAllVersions() {
        return listAllVersions(false);
    }

    @Override
    public List<String> listAllVersions(boolean includePrereleases) {
        Optional<MinecraftVersion> firstMappingsVersion = MinecraftVersion.fromId("1.14.4");
        if (firstMappingsVersion.isEmpty()) {
            Railroad.LOGGER.error("Failed to find first Minecraft version with Mojmap mappings (1.14.4)");
            return List.of();
        }

        return MinecraftVersion.getVersionsAfter(firstMappingsVersion.orElseThrow())
            .stream()
            .map(MinecraftVersion::id)
            .toList();
    }

    @Override
    public List<String> listVersionsFor(MinecraftVersion minecraftVersion) {
        return listVersionsFor(minecraftVersion, false);
    }

    @Override
    public List<String> listVersionsFor(MinecraftVersion minecraftVersion, boolean includePrereleases) {
        return Collections.singletonList(minecraftVersion.id());
    }

    @Override
    public void forceRefresh(boolean includePrereleases) {
        // No internal caching, nothing to refresh
    }
}
