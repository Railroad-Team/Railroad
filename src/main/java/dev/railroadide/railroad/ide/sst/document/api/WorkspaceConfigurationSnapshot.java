package dev.railroadide.railroad.ide.sst.document.api;

import java.util.Set;

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
 */
public record WorkspaceConfigurationSnapshot(
    long revision,
    Set<String> moduleIds
) {
    /**
     * Creates a configuration revision with an immutable copy of its module identities.
     *
     * @param revision nonnegative configuration revision
     * @param moduleIds module identities to copy
     * @throws IllegalArgumentException if the revision is negative
     * @throws NullPointerException if the module set or any module identity is {@code null}
     */
    public WorkspaceConfigurationSnapshot {
        if (revision < 0)
            throw new IllegalArgumentException("Revision cannot be negative");

        moduleIds = Set.copyOf(moduleIds);
    }
}
