package dev.railroadide.railroad.vcs.git.branch;

import dev.railroadide.railroad.vcs.git.identity.GitAuthor;
import org.jetbrains.annotations.Nullable;

/**
 * Snapshot of the latest commit associated with a branch.
 *
 * @param hash commit hash
 * @param timestampEpochSeconds commit timestamp in epoch seconds
 * @param message commit message
 * @param author commit author
 */
public record GitBranchLastCommit(
    @Nullable String hash,
    @Nullable Long timestampEpochSeconds,
    @Nullable String message,
    @Nullable GitAuthor author
) {
}
