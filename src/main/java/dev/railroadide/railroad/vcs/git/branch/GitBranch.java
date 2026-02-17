package dev.railroadide.railroad.vcs.git.branch;

import org.jetbrains.annotations.Nullable;

/**
 * Common contract for local and remote git branches.
 */
public sealed interface GitBranch permits GitBranch.LocalGitBranch, GitBranch.RemoteGitBranch {
    /**
     * Returns the branch display name.
     *
     * @return branch name
     */
    String name();

    /**
     * Returns last commit metadata when available.
     *
     * @return latest commit metadata, or {@code null} when unavailable
     */
    @Nullable GitBranchLastCommit lastCommit();

    /**
     * Returns the last commit hash when available.
     *
     * @return latest commit hash, or {@code null} when unavailable
     */
    default @Nullable String lastCommitHash() {
        return lastCommit() == null ? null : lastCommit().hash();
    }

    /**
     * Returns the computed branch status.
     *
     * @return branch status classification
     */
    GitBranchStatus status();

    /**
     * Returns whether this branch is a remote branch.
     *
     * @return {@code true} when the branch is remote
     */
    boolean isRemote();

    /**
     * Branch metadata for a local branch.
     *
     * @param name branch name
     * @param remoteName upstream remote branch reference
     * @param isCurrent whether this is the checked-out branch
     * @param aheadCount commits ahead of upstream
     * @param behindCount commits behind upstream
     * @param lastCommit last commit metadata
     * @param status computed branch status
     */
    record LocalGitBranch(
        String name,
        @Nullable String remoteName,
        boolean isCurrent,
        int aheadCount,
        int behindCount,
        @Nullable GitBranchLastCommit lastCommit,
        GitBranchStatus status
    ) implements GitBranch {
        /**
         * Returns {@code false} for local branches.
         *
         * @return always {@code false}
         */
        @Override
        public boolean isRemote() {
            return false;
        }
    }

    /**
     * Branch metadata for a remote-tracking branch.
     *
     * @param name branch name
     * @param remoteName remote name
     * @param lastCommit last commit metadata
     * @param status computed branch status
     */
    record RemoteGitBranch(
        String name,
        String remoteName,
        @Nullable GitBranchLastCommit lastCommit,
        GitBranchStatus status
    ) implements GitBranch {
        /**
         * Returns {@code true} for remote branches.
         *
         * @return always {@code true}
         */
        @Override
        public boolean isRemote() {
            return true;
        }
    }
}
