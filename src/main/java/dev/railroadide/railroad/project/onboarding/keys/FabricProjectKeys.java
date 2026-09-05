package dev.railroadide.railroad.project.onboarding.keys;

/**
 * Context keys for Fabric loader, API, access widener, and source layout settings.
 */
public class FabricProjectKeys {
    /**
     * Key for the selected Fabric loader version.
     */
    public static final String FABRIC_LOADER_VERSION = "fabric.loaderVersion";
    /**
     * Key for the selected Fabric API version string.
     */
    public static final String FABRIC_API_VERSION = "fabric.apiVersion";
    /**
     * Key for whether the generated Fabric project uses an access widener.
     */
    public static final String USE_ACCESS_WIDENER = "fabric.useAccessWidener";
    /**
     * Key for the access widener path entered during onboarding.
     */
    public static final String ACCESS_WIDENER_PATH = "fabric.accessWidenerPath";
    /**
     * Key for whether client sources are separated from common sources.
     */
    public static final String SPLIT_SOURCES = "fabric.splitSources";
}
