package dev.railroadide.railroad.vcs.git.diff;

/**
 * Additions and deletions totals for a single path in a diff.
 *
 * @param path file path
 * @param additions number of added lines
 * @param deletions number of deleted lines
 */
public record GitAdditionsDeletions(String path, int additions, int deletions) {
}
