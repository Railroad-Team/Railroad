package dev.railroadide.railroad.vcs.git.util;

import lombok.Data;

/**
 * Persisted git-related project settings.
 */
@Data
public final class GitSettings {
    /**
     * Requested interval between automatic repository status refreshes, in milliseconds.
     * The Git manager replaces null or nonpositive values with its default interval when loading settings.
     *
     * @param autoRefreshIntervalMillis requested interval in milliseconds, or null to leave it unset
     * @return the stored interval, or null when unset
     */
    private Long autoRefreshIntervalMillis;

    /** Creates settings with no automatic refresh interval configured. */
    public GitSettings() {
    }

    /**
     * Creates settings with the supplied automatic refresh interval, without validating it.
     *
     * @param autoRefreshIntervalMillis requested interval in milliseconds, or null to leave it unset
     */
    public GitSettings(Long autoRefreshIntervalMillis) {
        this.autoRefreshIntervalMillis = autoRefreshIntervalMillis;
    }
}
