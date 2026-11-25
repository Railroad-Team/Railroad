package dev.railroadide.railroad.debug.source;

import java.nio.file.Path;
import java.util.List;
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

    /**
     * Returns the source roots that dependency sources resolved by this index live under. Breakpoints in files below
     * these roots belong to dependencies, and the roots map such files back to runtime class names.
     *
     * @return dependency source roots, empty by default
     */
    default List<Path> sourceRoots() {
        return List.of();
    }
}
