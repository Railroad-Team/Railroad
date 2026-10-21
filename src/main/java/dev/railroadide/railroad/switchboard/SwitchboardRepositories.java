package dev.railroadide.railroad.switchboard;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.switchboard.cache.CacheManager;
import dev.railroadide.railroad.switchboard.cache.impl.DelegatingCacheManager;
import dev.railroadide.railroad.switchboard.cache.impl.JsonCacheManager;
import dev.railroadide.railroad.switchboard.cache.impl.SqlCacheManager;
import dev.railroadide.railroad.switchboard.repositories.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Provides the application-wide Switchboard repositories.
 *
 * <p>
 * Loading this class creates the cache manager and registers each repository in
 * {@link SwitchboardRepository#REGISTRY}.
 * </p>
 */
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

    /** Repository for Minecraft versions. */
    public static final MinecraftVersionRepository MINECRAFT = register("railroad:switchboard/minecraft",
        new MinecraftVersionRepository(CLIENT, CACHE_MANAGER));
    /** Repository for Forge versions. */
    public static final ForgeVersionRepository FORGE = register("railroad:switchboard/forge",
        new ForgeVersionRepository(CLIENT, CACHE_MANAGER));
    /** Repository for NeoForge versions. */
    public static final NeoforgeVersionRepository NEOFORGE = register("railroad:switchboard/neoforge",
        new NeoforgeVersionRepository(CLIENT, CACHE_MANAGER));
    /** Repository for Fabric API versions. */
    public static final FabricApiVersionRepository FABRIC_API = register("railroad:switchboard/fabric_api",
        new FabricApiVersionRepository(CLIENT, CACHE_MANAGER));
    /** Repository for Fabric Loader versions. */
    public static final FabricLoaderVersionRepository FABRIC_LOADER = register("railroad:switchboard/fabric_loader",
        new FabricLoaderVersionRepository(CLIENT, CACHE_MANAGER));
    /** Repository for Yarn versions. */
    public static final YarnVersionRepository YARN = register("railroad:switchboard/yarn",
        new YarnVersionRepository(CLIENT, CACHE_MANAGER));
    /** Repository for Mojmap versions. */
    public static final MojmapVersionRepository MOJMAP = register("railroad:switchboard/mojmap",
        new MojmapVersionRepository(CLIENT, CACHE_MANAGER));
    /** Repository for MCP versions. */
    public static final McpVersionRepository MCP = register("railroad:switchboard/mcp",
        new McpVersionRepository(CLIENT, CACHE_MANAGER));
    /** Repository for Parchment versions. */
    public static final ParchmentVersionRepository PARCHMENT = register("railroad:switchboard/parchment",
        new ParchmentVersionRepository(CLIENT, CACHE_MANAGER));

    private SwitchboardRepositories() {
    }

    /**
     * Initializes Switchboard repository registration.
     *
     * <p>
     * This method intentionally does nothing; invoking it forces this class to
     * initialize when callers need deterministic registration timing.
     * </p>
     */
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
