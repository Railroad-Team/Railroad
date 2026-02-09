package dev.railroadide.railroad.vcs.git.branch;

import org.jetbrains.annotations.Nullable;

public sealed interface GitBranch permits GitBranch.LocalGitBranch, GitBranch.RemoteGitBranch {
    String name();

    @Nullable GitBranchLastCommit lastCommit();

    default @Nullable String lastCommitHash() {
        return lastCommit() == null ? null : lastCommit().hash();
    }

    GitBranchStatus status();

    boolean isRemote();

    record LocalGitBranch(
        String name,
        @Nullable String remoteName,
        boolean isCurrent,
        int aheadCount,
        int behindCount,
        @Nullable GitBranchLastCommit lastCommit,
        GitBranchStatus status
    ) implements GitBranch {
        @Override
        public boolean isRemote() {
            return false;
        }
    }

    record RemoteGitBranch(
        String name,
        String remoteName,
        @Nullable GitBranchLastCommit lastCommit,
        GitBranchStatus status
    ) implements GitBranch {
        @Override
        public boolean isRemote() {
            return true;
        }
    }
}
