package dev.railroadide.railroad.vcs.git;

import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.vcs.git.commit.GitCommitData;
import dev.railroadide.railroad.vcs.git.diff.GitDiffMode;
import dev.railroadide.railroad.vcs.git.remote.GitRemote;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import dev.railroadide.railroad.vcs.git.util.GitPushStrategy;
import dev.railroadide.railroad.vcs.git.util.GitRepository;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Factory for building reusable git CLI command definitions.
 */
public final class GitCommands {
    private GitCommands() {
    }

    /**
     * Builds `git status --porcelain=v1 -b -z`.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand statusPorcelainV1Z(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("status", "--porcelain=v1", "-b", "-z")
            .build();
    }

    /**
     * Builds `git rev-parse --is-inside-work-tree`.
     *
     * @param repoPath path to test
     * @return configured git command
     */
    public static GitCommand revParseIsInsideWorkTree(Path repoPath) {
        return GitCommand.builder()
            .workingDirectory(repoPath)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("rev-parse", "--is-inside-work-tree")
            .build();
    }

    /**
     * Builds `git rev-parse --show-toplevel`.
     *
     * @param repoPath repository path
     * @return configured git command
     */
    public static GitCommand revParseShowTopLevel(Path repoPath) {
        return GitCommand.builder()
            .workingDirectory(repoPath)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("rev-parse", "--show-toplevel")
            .build();
    }

    /**
     * Builds `git add --` for the provided file paths.
     *
     * @param repo target repository
     * @param filePaths file paths to stage
     * @return configured git command
     */
    public static GitCommand stageFiles(GitRepository repo, String... filePaths) {
        GitCommand.Builder builder = GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("add", "--")
            .addArgs(filePaths);

        return builder.build();
    }

    /**
     * Builds `git restore --staged --` for the provided file paths.
     *
     * @param repo target repository
     * @param filePaths file paths to unstage
     * @return configured git command
     */
    public static GitCommand unstageFiles(GitRepository repo, String... filePaths) {
        GitCommand.Builder builder = GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("restore", "--staged", "--")
            .addArgs(filePaths);

        return builder.build();
    }

    /**
     * Builds a `git commit` command from commit data.
     *
     * @param repo target repository
     * @param commit commit request data
     * @return configured git command
     */
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

    /**
     * Builds `git push --progress`.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand push(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(15, TimeUnit.SECONDS)
            .addArgs("push", "--progress")
            .build();
    }

    /**
     * Builds `git remote -v`.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand remoteGetUrls(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("remote", "-v")
            .build();
    }

    /**
     * Builds command to resolve current upstream branch.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand getUpstream(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("rev-parse", "--abbrev-ref", "--symbolic-full-name", "@{u}")
            .build();
    }

    /**
     * Builds `git fetch --prune --progress`.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand fetch(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(30, TimeUnit.SECONDS)
            .addArgs("fetch", "--prune", "--progress")
            .build();
    }

    /**
     * Builds `git pull --ff-only --progress`.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand pull(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(30, TimeUnit.SECONDS)
            .addArgs("pull", "--ff-only", "--progress")
            .build();
    }

    /**
     * Builds command to read configured git username.
     *
     * @return configured git command
     */
    public static GitCommand getUserName() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("config", "--get", "user.name")
            .build();
    }

    /**
     * Builds command to read configured git user email.
     *
     * @return configured git command
     */
    public static GitCommand getUserEmail() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("config", "--get", "user.email")
            .build();
    }

    /**
     * Builds command to read `commit.gpgsign`.
     *
     * @return configured git command
     */
    public static GitCommand getCommitGpgSign() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("config", "--get", "commit.gpgsign")
            .build();
    }

    /**
     * Builds command to read `gpg.format`.
     *
     * @return configured git command
     */
    public static GitCommand getGpgFormat() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("config", "--get", "gpg.format")
            .build();
    }

    /**
     * Builds command to read `user.signingkey`.
     *
     * @return configured git command
     */
    public static GitCommand getUserSigningKey() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("config", "--get", "user.signingkey")
            .build();
    }

    /**
     * Builds command to read `gpg.program`.
     *
     * @return configured git command
     */
    public static GitCommand getGpgProgram() {
        return GitCommand.builder()
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("config", "--get", "gpg.program")
            .build();
    }

    /**
     * Builds command to query git version.
     *
     * @return configured git command
     */
    public static GitCommand getGitVersion() {
        Long timeoutMs = Settings.GIT_VERSION_COMMAND_TIMEOUT_MS.getOrDefaultValue();
        return GitCommand.builder()
            .timeout(timeoutMs, TimeUnit.MILLISECONDS)
            .addArgs("--version")
            .build();
    }

    /**
     * Builds command to retrieve recent commits with an optional cursor.
     *
     * @param repo target repository
     * @param cursor optional cursor hash
     * @param limit maximum number of commits
     * @return configured git command
     */
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

    /**
     * Builds command to get unstaged diff text for a file.
     *
     * @param repo target repository
     * @param filePath file path to diff
     * @param contextLines number of context lines
     * @return configured git command
     */
    public static GitCommand getUnstagedDiff(GitRepository repo, Path filePath, int contextLines) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs("--no-pager", "diff", "--no-color", "--unified=%d".formatted(contextLines), "--", filePath.toString())
            .build();
    }

    /**
     * Builds command to get unstaged diff text using default context lines.
     *
     * @param repo target repository
     * @param filePath file path to diff
     * @return configured git command
     */
    public static GitCommand getUnstagedDiff(GitRepository repo, Path filePath) {
        return getUnstagedDiff(repo, filePath, 3);
    }

    /**
     * Builds command to get file diff for the requested mode.
     *
     * @param repo target repository
     * @param change file change descriptor
     * @param mode diff mode
     * @return configured git command
     */
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

    /**
     * Builds command to resolve HEAD commit hash.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand getHeadCommitHash(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("rev-parse", "HEAD")
            .build();
    }

    /**
     * Builds command to list tags that point to a commit.
     *
     * @param repo target repository
     * @param hash commit hash
     * @return configured git command
     */
    public static GitCommand getTagsPointingToCommit(GitRepository repo, String hash) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("tag", "--points-at", hash)
            .build();
    }

    /**
     * Builds command to list all tags with their commit refs.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand getAllTagsWithCommits(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("show-ref", "--tags", "-d")
            .build();
    }

    /**
     * Builds command to list all local and remote branches.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand getAllBranches(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("branch", "--all", "--no-color", "--format=%(refname:short)")
            .build();
    }

    /**
     * Builds command to list local branches.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand getAllLocalBranches(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("branch", "--no-color", "--format=%(refname:short)")
            .build();
    }

    /**
     * Builds command to list remote branches.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand getAllRemoteBranches(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs("branch", "--remotes", "--no-color", "--format=%(refname:short)")
            .build();
    }

    /**
     * Builds command to list repository authors via shortlog.
     *
     * @param repo target repository
     * @param includeEmails whether to include email addresses
     * @return configured git command
     */
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

    /**
     * Builds command to get the root commit timestamp.
     *
     * @param repo target repository
     * @return configured git command
     */
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

    /**
     * Builds command to get additions/deletions stats for a commit.
     *
     * @param repo target repository
     * @param hash commit hash
     * @return configured git command
     */
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

    /**
     * Builds command to get full commit message for a commit.
     *
     * @param repo target repository
     * @param hash commit hash
     * @return configured git command
     */
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

    /**
     * Builds command to create a stash entry.
     *
     * @param repo target repository
     * @param message stash message
     * @param includeUntracked whether untracked files should be included
     * @return configured git command
     */
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

    /**
     * Builds command to pop the latest stash.
     *
     * @param repo target repository
     * @return configured git command
     */
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

    /**
     * Builds command to pop a specific stash reference.
     *
     * @param repo target repository
     * @param stashRef stash reference
     * @return configured git command
     */
    public static GitCommand stashPop(GitRepository repo, String stashRef) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "stash",
                "pop",
                stashRef
            )
            .build();
    }

    /**
     * Builds command to list stash entries.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand getStashes(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "--no-pager",
                "stash",
                "list",
                "--pretty=format:%gd%x1f%H%x1f%ct%x1f%gs"
            )
            .build();
    }

    /**
     * Builds command to apply a stash without dropping it.
     *
     * @param repo target repository
     * @param stashRef stash reference
     * @return configured git command
     */
    public static GitCommand stashApply(GitRepository repo, String stashRef) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "stash",
                "apply",
                stashRef
            )
            .build();
    }

    /**
     * Builds command to drop a stash entry.
     *
     * @param repo target repository
     * @param stashRef stash reference
     * @return configured git command
     */
    public static GitCommand stashDrop(GitRepository repo, String stashRef) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "stash",
                "drop",
                stashRef
            )
            .build();
    }

    /**
     * Builds command to list changed files inside a stash.
     *
     * @param repo target repository
     * @param stashRef stash reference
     * @return configured git command
     */
    public static GitCommand getStashChanges(GitRepository repo, String stashRef) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "--no-pager",
                "diff",
                "--name-status",
                "-z",
                stashRef + "^1",
                stashRef
            )
            .build();
    }

    /**
     * Builds command to get file diff for a stash entry.
     *
     * @param repo target repository
     * @param stashRef stash reference
     * @param filePath file path within the stash
     * @return configured git command
     */
    public static GitCommand getStashDiff(GitRepository repo, String stashRef, Path filePath) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "--no-pager",
                "diff",
                "--no-color",
                stashRef + "^1",
                stashRef,
                "--",
                filePath.toString()
            )
            .build();
    }

    /**
     * Builds detached checkout command using `git switch`.
     *
     * @param repo target repository
     * @param hash commit hash
     * @return configured git command
     */
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

    /**
     * Builds detached checkout command using `git checkout`.
     *
     * @param repo target repository
     * @param hash commit hash
     * @return configured git command
     */
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

    /**
     * Builds hard reset command.
     *
     * @param repo target repository
     * @return configured git command
     */
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

    /**
     * Builds command to remove untracked files and directories.
     *
     * @param repo target repository
     * @return configured git command
     */
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

    /**
     * Builds command to validate a branch name.
     *
     * @param repo target repository
     * @param string branch name candidate
     * @return configured git command
     */
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

    /**
     * Builds command to create a branch at a hash.
     *
     * @param repo target repository
     * @param branchName new branch name
     * @param hash start-point hash
     * @return configured git command
     */
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

    /**
     * Builds command to switch to a branch.
     *
     * @param repo target repository
     * @param branchName branch name
     * @return configured git command
     */
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

    /**
     * Builds command to checkout a branch.
     *
     * @param repo target repository
     * @param branchName branch name
     * @return configured git command
     */
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

    /**
     * Builds command to verify tag existence.
     *
     * @param repo target repository
     * @param tagName tag name
     * @return configured git command
     */
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

    /**
     * Builds command to validate tag name format.
     *
     * @param repo target repository
     * @param tagName tag name candidate
     * @return configured git command
     */
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

    /**
     * Builds command to create or update a tag.
     *
     * @param repo target repository
     * @param tagName tag name
     * @param hash target commit hash
     * @param message optional annotated tag message
     * @param overwrite whether existing tags may be overwritten
     * @return configured git command
     */
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

    /**
     * Builds command to test whether cherry-pick state is active.
     *
     * @param repo target repository
     * @return configured git command
     */
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

    /**
     * Builds command to cherry-pick a commit.
     *
     * @param repo target repository
     * @param commitHash commit hash to cherry-pick
     * @return configured git command
     */
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

    /**
     * Builds command to continue an in-progress cherry-pick.
     *
     * @param repo target repository
     * @return configured git command
     */
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

    /**
     * Builds command to abort an in-progress cherry-pick.
     *
     * @param repo target repository
     * @return configured git command
     */
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

    /**
     * Builds command to quit cherry-pick state.
     *
     * @param repo target repository
     * @return configured git command
     */
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

    /**
     * Builds command to revert a commit.
     *
     * @param repo target repository
     * @param commitHash commit hash to revert
     * @return configured git command
     */
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

    /**
     * Builds command to resolve a branch's upstream tracking reference.
     *
     * @param repo target repository
     * @param branchName local branch name
     * @return configured git command
     */
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

    /**
     * Builds command to get ahead/behind counts between branches.
     *
     * @param repo target repository
     * @param branchName local branch
     * @param upstreamBranch upstream branch
     * @return configured git command
     */
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

    /**
     * Builds command to resolve a branch commit hash.
     *
     * @param repo target repository
     * @param branchName branch name
     * @return configured git command
     */
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

    /**
     * Builds command to read last commit timestamp for a branch.
     *
     * @param repo target repository
     * @param branchName branch name
     * @return configured git command
     */
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

    /**
     * Builds command to read commit author name and email.
     *
     * @param repo target repository
     * @param hash commit hash
     * @return configured git command
     */
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

    /**
     * Builds command to set upstream branch for a local branch.
     *
     * @param repo target repository
     * @param branchName local branch name
     * @param upstreamBranch upstream branch name
     * @return configured git command
     */
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

    /**
     * Builds command to unset upstream branch for a local branch.
     *
     * @param repo target repository
     * @param branchName local branch name
     * @return configured git command
     */
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

    /**
     * Builds command to delete a local branch.
     *
     * @param repo target repository
     * @param branchName branch name
     * @param force whether to force delete
     * @return configured git command
     */
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

    /**
     * Builds command to rename a branch.
     *
     * @param repo target repository
     * @param oldName current branch name
     * @param newName new branch name
     * @param force whether rename should force
     * @return configured git command
     */
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

    /**
     * Builds command to list all URLs for a remote.
     *
     * @param repo target repository
     * @param remote remote descriptor
     * @return configured git command
     */
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

    /**
     * Builds command to add a remote.
     *
     * @param repo target repository
     * @param name remote name
     * @param fetchUrl fetch URL
     * @return configured git command
     */
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

    /**
     * Builds command to rename a remote.
     *
     * @param repo target repository
     * @param oldName current remote name
     * @param newName new remote name
     * @return configured git command
     */
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

    /**
     * Builds command to set remote fetch URL.
     *
     * @param repo target repository
     * @param name remote name
     * @param fetchUrl fetch URL
     * @return configured git command
     */
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

    /**
     * Builds command to set remote push URL.
     *
     * @param repo target repository
     * @param name remote name
     * @param pushUrl push URL
     * @return configured git command
     */
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

    /**
     * Builds command to remove a remote.
     *
     * @param repo target repository
     * @param name remote name
     * @return configured git command
     */
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

    /**
     * Builds command to read remote prune setting.
     *
     * @param repo target repository
     * @param remote remote descriptor
     * @return configured git command
     */
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

    /**
     * Builds command to fetch and prune all remotes.
     *
     * @param repo target repository
     * @return configured git command
     */
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

    /**
     * Builds command to prune all remotes.
     *
     * @param repo target repository
     * @return configured git command
     */
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

    /**
     * Builds command to run git garbage collection.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand gc(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(30, TimeUnit.SECONDS)
            .addArgs(
                "gc"
            )
            .build();
    }

    /**
     * Builds command to read `pull.ff`.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand isPullFastForwardOnly(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "config",
                "--get",
                "pull.ff"
            )
            .build();
    }

    /**
     * Builds command to read branch-specific rebase setting.
     *
     * @param repo target repository
     * @param branchName branch name
     * @return configured git command
     */
    public static GitCommand getBranchRebaseSetting(GitRepository repo, String branchName) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "config",
                "--get",
                "branch." + branchName + ".rebase"
            )
            .build();
    }

    /**
     * Builds command to read global pull rebase setting.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand getGlobalRebaseSetting(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "config",
                "--get",
                "pull.rebase"
            )
            .build();
    }

    /**
     * Builds command to read `push.default`.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand getPushDefault(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "config",
                "--get",
                "push.default"
            )
            .build();
    }

    /**
     * Builds command to unset `push.default`.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand unsetPushDefault(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "config",
                "--unset",
                "push.default"
            )
            .build();
    }

    /**
     * Builds command to set `push.default`.
     *
     * @param repo target repository
     * @param strategy push strategy
     * @return configured git command
     */
    public static GitCommand setPushDefault(GitRepository repo, GitPushStrategy strategy) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "config",
                "push.default",
                strategy.name().toLowerCase()
            )
            .build();
    }

    /**
     * Builds command to unset `pull.rebase`.
     *
     * @param repo target repository
     * @return configured git command
     */
    public static GitCommand unsetPullRebase(GitRepository repo) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "config",
                "--unset",
                "pull.rebase"
            )
            .build();
    }

    /**
     * Builds command to set `pull.rebase`.
     *
     * @param repository target repository
     * @param shouldRebase whether pull should rebase
     * @return configured git command
     */
    public static GitCommand setPullRebase(GitRepository repository, boolean shouldRebase) {
        return GitCommand.builder()
            .workingDirectory(repository)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "config",
                "pull.rebase",
                shouldRebase ? "true" : "false"
            )
            .build();
    }

    /**
     * Builds command to set `pull.ff`.
     *
     * @param repository target repository
     * @param ffOnly whether fast-forward only mode should be enabled
     * @return configured git command
     */
    public static GitCommand setPullFastForwardOnly(GitRepository repository, boolean ffOnly) {
        return GitCommand.builder()
            .workingDirectory(repository)
            .timeout(5, TimeUnit.SECONDS)
            .addArgs(
                "config",
                "pull.ff",
                ffOnly ? "only" : "false"
            )
            .build();
    }

    /**
     * Builds command to list commits between two references.
     *
     * @param repo target repository
     * @param branchA left-side reference
     * @param branchB right-side reference
     * @return configured git command
     */
    public static GitCommand getCommitsBetween(GitRepository repo, String branchA, String branchB) {
        return GitCommand.builder()
            .workingDirectory(repo)
            .timeout(10, TimeUnit.SECONDS)
            .addArgs(
                "--no-pager",
                "log",
                "--first-parent",
                "--date=unix",
                "--pretty=format:%H%x00%h%x00%s%x00%an%x00%ae%x00%at%x00%cn%x00%ce%x00%ct%x00%P%x1e",
                branchA + ".." + branchB
            )
            .build();
    }
}
