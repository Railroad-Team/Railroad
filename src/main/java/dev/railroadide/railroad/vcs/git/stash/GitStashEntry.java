package dev.railroadide.railroad.vcs.git.stash;

/**
 * Parsed metadata and summary stats for a stash entry.
 *
 * @param reference stash reference (for example, {@code stash@{0}})
 * @param branch source branch name
 * @param commitHash stash commit hash
 * @param createdAtEpochSeconds creation timestamp in epoch seconds
 * @param message stash message
 * @param additions total added lines
 * @param deletions total deleted lines
 */
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
