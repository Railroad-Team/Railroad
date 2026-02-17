package dev.railroadide.railroad.vcs.git.stash;

public record GitStashEntry(
    String reference,
    String branch,
    String commitHash,
    long createdAtEpochSeconds,
    String message,
    int additions,
    int deletions
) {
}
