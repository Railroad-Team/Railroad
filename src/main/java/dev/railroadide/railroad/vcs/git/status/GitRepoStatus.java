package dev.railroadide.railroad.vcs.git.status;

import java.util.List;

/**
 * Repository status snapshot including branch and working tree state.
 *
 * @param branch current branch name
 * @param ahead commits ahead of upstream
 * @param behind commits behind upstream
 * @param changes working tree and index changes
 */
public record GitRepoStatus(
    String branch,
    int ahead,
    int behind,
    List<GitFileChange> changes
) {}
