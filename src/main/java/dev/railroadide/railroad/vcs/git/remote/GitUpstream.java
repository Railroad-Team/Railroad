package dev.railroadide.railroad.vcs.git.remote;

/**
 * Upstream tracking branch reference for the current local branch.
 *
 * @param remoteName remote name
 * @param branchName branch name on the remote
 */
public record GitUpstream(String remoteName, String branchName) {
}
