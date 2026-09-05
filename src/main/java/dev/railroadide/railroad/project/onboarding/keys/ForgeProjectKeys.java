package dev.railroadide.railroad.project.onboarding.keys;

/**
 * Context keys for Forge version, metadata, and optional development features.
 */
public class ForgeProjectKeys {
    /**
     * Key for the selected Forge or NeoForge version.
     */
    public static final String FORGE_VERSION = "forge.forgeVersion";
    /**
     * Key for whether the project enables mixins.
     */
    public static final String USE_MIXINS = "forge.useMixins";
    /**
     * Key for whether the project uses an access transformer.
     */
    public static final String USE_ACCESS_TRANSFORMER = "forge.useAccessTransformer";
    /**
     * Key for whether Gradle should generate IDE run configurations.
     */
    public static final String GEN_RUN_FOLDERS = "forge.genRunFolders";
    /**
     * Key for the mod update metadata URL.
     */
    public static final String UPDATE_JSON_URL = "forge.updateJsonUrl";
    /**
     * Key for the mod compatibility display test.
     */
    public static final String DISPLAY_TEST = "forge.displayTest";
    /**
     * Key for whether the mod is restricted to the client side.
     */
    public static final String CLIENT_SIDE_ONLY = "forge.clientSideOnly";
    /**
     * Key for the mod website displayed in its metadata.
     */
    public static final String DISPLAY_URL = "project.displayUrl";
}
