package dev.railroadide.railroad.vcs.git;

import dev.railroadide.railroad.vcs.git.commit.GitCommitData;
import dev.railroadide.railroad.vcs.git.diff.GitDiffMode;
import dev.railroadide.railroad.vcs.git.remote.GitRemote;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import dev.railroadide.railroad.vcs.git.util.GitRepository;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class GitCommands {
    private GitCommands() {
    }

    public static GitCommand statusPorcelainV1Z(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("status", "--porcelain=v1", "-b", "-z")
            .build();
    }

    public static GitCommand revParseIsInsideWorkTree(Path repoPath) {
        return GitCommand.builder()
            .workingDirectory(repoPath)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("rev-parse", "--is-inside-work-tree")
            .build();
    }

    public static GitCommand revParseShowTopLevel(Path repoPath) {
        return GitCommand.builder()
            .workingDirectory(repoPath)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("rev-parse", "--show-toplevel")
            .build();
    }

    public static GitCommand stageFiles(GitRepository repo, String... filePaths) {
        GitCommand.Builder builder = GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("add", "--")
            .addArgs(filePaths);

        return builder.build();
    }

    public static GitCommand unstageFiles(GitRepository repo, String... filePaths) {
        GitCommand.Builder builder = GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("restore", "--staged", "--")
            .addArgs(filePaths);

        return builder.build();
    }

    public static GitCommand commit(GitRepository repo, GitCommitData commit) {
        GitCommand.Builder builder = GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs("commit", "-m", commit.message());

        if (commit.description() != null && !commit.description().isBlank()) {
            builder.addArgs("-m", commit.description());
        }

        if (commit.amend()) {
            builder.addArgs("--amend");
        }

        if (commit.signOff()) {
            builder.addArgs("--signoff");
        }

        List<GitFileChange> fileChanges = commit.selectedChanges();
        if (!fileChanges.isEmpty()) {
            builder.addArgs("--");
            for (GitFileChange change : fileChanges) {
                if (change == null || change.path() == null)
                    continue;

                builder.addArgs(change.path());
            }
        }

        return builder.build();
    }

    public static GitCommand push(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(15, TimeUnit.SECONDS)
            .addArgs("push", "--progress")
            .build();
    }

    public static GitCommand remoteGetUrls(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("remote", "-v")
            .build();
    }

    public static GitCommand getUpstream(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
            .build();
    }

    public static GitCommand fetch(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(30, TimeUnit.SECONDS)
            .addArgs("fetch", "--prune", "--progress")
            .build();
    }

    public static GitCommand pull(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(30, TimeUnit.SECONDS)
            .addArgs("pull", "--ff-only", "--progress")
            .build();
    }

    public static GitCommand getUserName() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("config", "--get", "user.name")
            .build();
    }

    public static GitCommand getUserEmail() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("config", "--get", "user.email")
            .build();
    }

    public static GitCommand getCommitGpgSign() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("config", "--get", "commit.gpgsign")
            .build();
    }

    public static GitCommand getGpgFormat() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("config", "--get", "gpg.format")
            .build();
    }

    public static GitCommand getUserSigningKey() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("config", "--get", "user.signingkey")
            .build();
    }

    public static GitCommand getGpgProgram() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("config", "--get", "gpg.program")
            .build();
    }

    public static GitCommand getGitVersion() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("--version")
            .build();
    }

    public static GitCommand getRecentCommits(GitRepository repo, @Nullable String cursor, int limit) {
        GitCommand.Builder builder = GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "--no-pager",
                "log",
                "--first-parent",
                "-n", String.valueOf(limit),
                "--date=unix",
                "--pretty=format:%H%x00%h%x00%s%x00%an%x00%ae%x00%at%x00%cn%x00%ce%x00%ct%x00%P%x1e");

        if (cursor != null && !cursor.isBlank()) {
            builder.addArgs("--skip=1", cursor.strip());
        }

        return builder.build();
    }

    public static GitCommand getUnstagedDiff(GitRepository repo, Path filePath, int contextLines) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs("--no-pager", "diff", "--no-color", "--unified=%d".formatted(contextLines), "--", filePath.toString())
            .build();
    }

    public static GitCommand getUnstagedDiff(GitRepository repo, Path filePath) {
        return getUnstagedDiff(repo, filePath, 3);
    }

    public static GitCommand getDiff(GitRepository repo, GitFileChange change, GitDiffMode mode) {
        GitCommand.Builder builder = GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs("--no-pager", "diff", "--no-color");

        switch (mode) {
            case STAGED -> builder.addArgs("--cached");
            case HEAD -> builder.addArgs("HEAD");
        }

        builder.addArgs("--", change.path());

        return builder.build();
    }

    public static GitCommand getHeadCommitHash(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("rev-parse", "HEAD")
            .build();
    }

    public static GitCommand getTagsPointingToCommit(GitRepository repo, String hash) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("tag", "--points-at", hash)
            .build();
    }

    public static GitCommand getAllTagsWithCommits(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("show-ref", "--tags", "-d")
            .build();
    }

    public static GitCommand getAllBranches(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("branch", "--all", "--no-color", "--format=%(refname:short)")
            .build();
    }

    public static GitCommand getAllLocalBranches(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("branch", "--no-color", "--format=%(refname:short)")
            .build();
    }

    public static GitCommand getAllRemoteBranches(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("branch", "--remotes", "--no-color", "--format=%(refname:short)")
            .build();
    }

    public static GitCommand getAllAuthors(GitRepository repo, boolean includeEmails) {
        GitCommand.Builder builder = GitCommand.builder()
            .workingDirectory(repo)
            .timeout(1, TimeUnit.MINUTES)
            .addArgs(
                "--no-pager",
                "shortlog",
                "--summary",
                "--numbered"
            );
        if (includeEmails) {
            builder.addArgs("--email");
        }
        builder.addArgs("HEAD");
        return builder.build();
    }

    public static GitCommand getRepositoryCreationDate(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "--no-pager",
                "log",
                "--reverse",
                "--pretty=format:%at",
                "--max-parents=0",
                "-n", "1"
            )
            .build();
    }

    public static GitCommand getAdditionsDeletions(GitRepository repo, String hash) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "--no-pager",
                "show",
                "--pretty=format:",
                "--numstat",
                hash
            )
            .build();
    }

    public static GitCommand getCommitMessage(GitRepository repo, String hash) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "--no-pager",
                "log",
                "-n", "1",
                "--format=%B",
                hash
            )
            .build();
    }

    public static GitCommand stashSave(GitRepository repo, String message, boolean includeUntracked) {
        GitCommand.Builder builder = GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "stash",
                "push",
                "-m", message
            );

        if (includeUntracked) {
            builder.addArgs("-u");
        }

        return builder.build();
    }

    public static GitCommand stashPop(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "stash",
                "pop"
            )
            .build();
    }

    public static GitCommand checkoutDetachedWithSwitch(GitRepository repo, String hash) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "switch",
                "--detach",
                hash
            )
            .build();
    }

    public static GitCommand checkoutDetached(GitRepository repo, String hash) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "checkout",
                "--detach",
                hash
            )
            .build();
    }

    public static GitCommand resetHard(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "reset",
                "--hard"
            )
            .build();
    }

    public static GitCommand cleanUntrackedFiles(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "clean",
                "-fd"
            )
            .build();
    }

    public static GitCommand checkValidBranchName(GitRepository repo, String string) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "check-ref-format",
                "--branch",
                string
            )
            .build();
    }

    public static GitCommand createBranch(GitRepository repo, String branchName, String hash) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "branch",
                branchName,
                hash
            )
            .build();
    }

    public static GitCommand checkoutBranchWithSwitch(GitRepository repo, String branchName) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "switch",
                branchName
            )
            .build();
    }

    public static GitCommand checkoutBranch(GitRepository repo, String branchName) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "checkout",
                branchName
            )
            .build();
    }

    public static GitCommand checkTagExists(GitRepository repo, String tagName) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "rev-parse",
                "-q",
                "--verify",
                "refs/tags/" + tagName
            )
            .build();
    }

    public static GitCommand checkValidTagName(GitRepository repo, String tagName) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "check-ref-format",
                "refs/tags/" + tagName
            )
            .build();
    }

    public static GitCommand createTag(GitRepository repo, String tagName, String hash, @Nullable String message, boolean overwrite) {
        GitCommand.Builder builder = GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "tag"
            );

        if (overwrite) {
            builder.addArgs("-f");
        }

        if (message != null && !message.isBlank()) {
            builder.addArgs("-a", tagName, "-m", message);
        } else {
            builder.addArgs(tagName);
        }

        builder.addArgs(hash);

        return builder.build();
    }

    public static GitCommand checkCherryPickState(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "rev-parse",
                "-q",
                "--verify",
                "CHERRY_PICK_HEAD"
            )
            .build();
    }

    public static GitCommand cherryPickCommit(GitRepository repo, String commitHash) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(15, TimeUnit.SECONDS)
            .addArgs(
                "cherry-pick",
                "-x",
                "--no-edit",
                commitHash
            )
            .build();
    }

    public static GitCommand continueCherryPick(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(15, TimeUnit.SECONDS)
            .addArgs(
                "cherry-pick",
                "--continue"
            )
            .build();
    }

    public static GitCommand abortCherryPick(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(15, TimeUnit.SECONDS)
            .addArgs(
                "cherry-pick",
                "--abort"
            )
            .build();
    }

    public static GitCommand quitCherryPick(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(15, TimeUnit.SECONDS)
            .addArgs(
                "cherry-pick",
                "--quit"
            )
            .build();
    }

    public static GitCommand revertCommit(GitRepository repo, String commitHash) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(15, TimeUnit.SECONDS)
            .addArgs(
                "revert",
                "-x",
                "--no-edit",
                commitHash
            )
            .build();
    }

    public static GitCommand getRemoteTrackingBranch(GitRepository repo, String branchName) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "rev-parse",
                "--abbrev-ref",
                "--symbolic-full-name",
                "\"" + branchName + "@{u}\""
            )
            .build();
    }

    public static GitCommand getAheadBehindCount(GitRepository repo, String branchName, String upstreamBranch) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "rev-list",
                "--left-right",
                "--count",
                branchName + "..." + upstreamBranch
            )
            .build();
    }

    public static GitCommand getLastCommitHash(GitRepository repo, String branchName) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "rev-parse",
                branchName
            )
            .build();
    }

    public static GitCommand getLastCommitTimestamp(GitRepository repo, String branchName) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "log",
                "-1",
                "--format=%ct",
                branchName
            )
            .build();
    }

    public static GitCommand getCommitAuthor(GitRepository repo, String hash) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "--no-pager",
                "log",
                "-n", "1",
                "--format=%an%x00%ae",
                hash
            )
            .build();
    }

    public static GitCommand setBranchUpstream(GitRepository repo, String branchName, String upstreamBranch) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "branch",
                "--set-upstream-to=" + upstreamBranch,
                branchName
            )
            .build();
    }

    public static GitCommand unsetBranchUpstream(GitRepository repo, String branchName) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "branch",
                "--unset-upstream",
                branchName
            )
            .build();
    }

    public static GitCommand deleteBranch(GitRepository repo, String branchName, boolean force) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "branch",
                "--delete",
                force ? "--force" : null,
                branchName
            )
            .build();
    }

    public static GitCommand renameBranch(GitRepository repo, String oldName, String newName, boolean force) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "branch",
                "--move",
                force ? "--force" : null,
                oldName,
                newName
            )
            .build();
    }

    public static GitCommand getRemoteUrls(GitRepository repo, GitRemote remote) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "remote",
                "get-url",
                "--all",
                remote.name()
            )
            .build();
    }

    public static GitCommand addRemote(GitRepository repo, String name, String fetchUrl) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "remote",
                "add",
                name,
                fetchUrl
            )
            .build();
    }

    public static GitCommand renameRemote(GitRepository repo, String oldName, String newName) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "remote",
                "rename",
                oldName,
                newName
            )
            .build();
    }

    public static GitCommand setRemoteFetchUrl(GitRepository repo, String name, String fetchUrl) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "remote",
                "set-url",
                name,
                fetchUrl
            )
            .build();
    }

    public static GitCommand setRemotePushUrl(GitRepository repo, String name, String pushUrl) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "remote",
                "set-url",
                "--push",
                name,
                pushUrl
            )
            .build();
    }

    public static GitCommand removeRemote(GitRepository repo, String name) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "remote",
                "remove",
                name
            )
            .build();
    }

    public static GitCommand isPruningEnabled(GitRepository repo, GitRemote remote) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "config",
                "--get",
                "remote." + remote.name() + ".prune"
            )
            .build();
    }

    public static GitCommand fetchAllRemotes(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(1, TimeUnit.MINUTES)
            .addArgs(
                "fetch",
                "--all",
                "--prune",
                "--progress"
            )
            .build();
    }

    public static GitCommand pruneAllRemotes(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(1, TimeUnit.MINUTES)
            .addArgs(
                "remote",
                "prune",
                "--all"
            )
            .build();
    }

    public static GitCommand gc(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(30, TimeUnit.SECONDS)
            .addArgs(
                "gc"
            )
            .build();
    }
}
