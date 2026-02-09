package dev.railroadide.railroad.vcs.git.branch;

import dev.railroadide.railroad.vcs.git.identity.GitAuthor;
import org.jetbrains.annotations.Nullable;

public record GitBranchLastCommit(
    @Nullable String hash,
    @Nullable Long timestampEpochSeconds,
    @Nullable String message,
    @Nullable GitAuthor author
) {
}
