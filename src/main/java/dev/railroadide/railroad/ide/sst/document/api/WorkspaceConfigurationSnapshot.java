package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Set;
import java.util.Map;

/**
 * Immutable configuration metadata captured with a workspace's documents.
 * Configuration revisions are assigned by the owner and are independent of both
 * workspace revisions and individual document versions. A configuration publication
 * can advance the workspace revision without changing any document.
 * <p>
 * Module identities are opaque, build-system-neutral strings supplied by the owner.
 * The set is defensively copied and cannot be modified through this snapshot.
 *
 * @param revision nonnegative configuration revision
 * @param moduleIds module identities belonging to this configuration
 * @param settings immutable project and module analysis settings keyed by qualified setting name
 */
public record WorkspaceConfigurationSnapshot(
    long revision,
    Set<String> moduleIds,
    Map<String, String> settings
) {
    /**
     * Creates configuration metadata without additional settings, for existing callers.
     *
     * @param revision configuration revision
     * @param moduleIds module identities
     */
    public WorkspaceConfigurationSnapshot(long revision, Set<String> moduleIds) {
        this(revision, moduleIds, Map.of());
    }
    /**
     * Creates a configuration revision with an immutable copy of its module identities.
     *
     * @param revision nonnegative configuration revision
     * @param moduleIds module identities to copy
     * @param settings immutable copies of project and module settings
     * @throws IllegalArgumentException if the revision is negative
     * @throws NullPointerException if the module set or any module identity is {@code null}
     */
    public WorkspaceConfigurationSnapshot {
        if (revision < 0)
            throw new IllegalArgumentException("Revision cannot be negative");

        moduleIds = Set.copyOf(moduleIds);
        settings = Map.copyOf(settings);
    }
}
