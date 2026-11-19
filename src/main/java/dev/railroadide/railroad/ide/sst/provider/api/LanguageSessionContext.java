package dev.railroadide.railroad.ide.sst.provider.api;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Captured project location, dialect and explicitly supplied host services for a session.
 *
 * @param projectRoot project directory, converted to an absolute normalized path
 * @param dialect dialect identifier, or an empty string for the default dialect
 * @param services immutable registry of host-provided services
 */
public record LanguageSessionContext(Path projectRoot, String dialect, CapabilityRegistry services) {
    /**
     * Creates session inputs with a normalized project root and non-null dialect and services.
     *
     * @param projectRoot project directory, converted to an absolute normalized path
     * @param dialect dialect identifier, or an empty string for the default dialect
     * @param services immutable registry of host-provided services
     */
    public LanguageSessionContext {
        projectRoot = Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
        Objects.requireNonNull(dialect, "dialect");
        Objects.requireNonNull(services, "services");
    }
}
