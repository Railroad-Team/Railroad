package dev.railroadide.railroad.switchboard;

import dev.railroadide.core.switchboard.SwitchboardRepository;
import dev.railroadide.core.switchboard.cache.CacheManager;
import dev.railroadide.core.switchboard.cache.impl.DelegatingCacheManager;
import dev.railroadide.core.switchboard.cache.impl.JsonCacheManager;
import dev.railroadide.core.switchboard.cache.impl.SqlCacheManager;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.switchboard.repositories.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

public final class SwitchboardRepositories {
    private static final SwitchboardClient CLIENT = new SwitchboardClient("https://switchboard.railroadide.dev/");
    private static final CacheManager CACHE_MANAGER;

    static {
        CacheManager cacheManager;
        try {
            cacheManager = createCacheManager();
        } catch (SQLException exception) {
            Railroad.LOGGER.error("Failed to initialize SQL cache manager, falling back to JSON cache", exception);
            Path cacheDirectory = ConfigHandler.getConfigDirectory().resolve("switchboard-cache");
            cacheManager = new DelegatingCacheManager(new JsonCacheManager(cacheDirectory, Railroad.GSON));
        }

        CACHE_MANAGER = cacheManager;
    }

    public static final MinecraftVersionRepository MINECRAFT = register("railroad:switchboard/minecraft", new MinecraftVersionRepository(CLIENT, CACHE_MANAGER));
    public static final ForgeVersionRepository FORGE = register("railroad:switchboard/forge", new ForgeVersionRepository(CLIENT, CACHE_MANAGER));
    public static final NeoforgeVersionRepository NEOFORGE = register("railroad:switchboard/neoforge", new NeoforgeVersionRepository(CLIENT, CACHE_MANAGER));
    public static final FabricApiVersionRepository FABRIC_API = register("railroad:switchboard/fabric_api", new FabricApiVersionRepository(CLIENT, CACHE_MANAGER));
    public static final FabricLoaderVersionRepository FABRIC_LOADER = register("railroad:switchboard/fabric_loader", new FabricLoaderVersionRepository(CLIENT, CACHE_MANAGER));
    public static final YarnVersionRepository YARN = register("railroad:switchboard/yarn", new YarnVersionRepository(CLIENT, CACHE_MANAGER));
    public static final MojmapVersionRepository MOJMAP = register("railroad:switchboard/mojmap", new MojmapVersionRepository(CLIENT, CACHE_MANAGER));
    public static final McpVersionRepository MCP = register("railroad:switchboard/mcp", new McpVersionRepository(CLIENT, CACHE_MANAGER));
    public static final ParchmentVersionRepository PARCHMENT = register("railroad:switchboard/parchment", new ParchmentVersionRepository(CLIENT, CACHE_MANAGER));

    private SwitchboardRepositories() {
    }

    public static void initialize() {
        // NO-OP: accessing this class ensures the repositories are registered.
    }

    private static CacheManager createCacheManager() throws SQLException {
        Path dbPath = ConfigHandler.getConfigDirectory().resolve("switchboard.db");
        if (Files.notExists(dbPath)) {
            try {
                Files.createFile(dbPath);
            } catch (IOException exception) {
                throw new SQLException("Failed to create database file", exception);
            }
        }

        return new DelegatingCacheManager(new SqlCacheManager(dbPath));
    }

    @SuppressWarnings("unchecked")
    private static <T extends SwitchboardRepository> T register(String id, T repository) {
        if (SwitchboardRepository.REGISTRY.contains(id))
            return repository;

        return (T) SwitchboardRepository.REGISTRY.register(id, repository);
    }
}
