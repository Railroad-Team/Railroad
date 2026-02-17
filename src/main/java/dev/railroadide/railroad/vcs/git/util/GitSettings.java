package dev.railroadide.railroad.vcs.git.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persisted git-related project settings.
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
public final class GitSettings {
    private Long autoRefreshIntervalMillis;
}
