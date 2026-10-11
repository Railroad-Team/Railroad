package dev.railroadide.railroad.debug.source;

import java.util.Optional;

/**
 * Finds dependency source content for a runtime source path and declaring type.
 */
public interface DependencySourceIndex {
    /**
     * An index that never resolves dependency sources.
     */
    DependencySourceIndex EMPTY = (_, _) -> Optional.empty();
    /**
     * Looks up dependency source content corresponding to a runtime location.
     *
     * @param runtimeSourcePath runtime source path, typically relative to a source root
     * @param declaringType qualified runtime declaring type name
     * @return the source identity, or empty when no dependency source can be found
     */
    Optional<DebugSource> resolve(String runtimeSourcePath, String declaringType);
}
