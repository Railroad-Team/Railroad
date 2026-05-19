package dev.railroadide.railroad.ide.diagnostics;

import dev.railroadide.railroad.plugin.spi.inspection.LanguageInspectionProvider;
import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;

/**
 * Global registries for language inspection extension points.
 */
public final class LanguageInspectionRegistries {
    public static final Registry<LanguageInspectionProvider> LANGUAGE_INSPECTION_PROVIDER_REGISTRY =
        RegistryManager.createOrderedRegistry("railroad:language_inspection_provider", LanguageInspectionProvider.class);

    private LanguageInspectionRegistries() {
    }
}
