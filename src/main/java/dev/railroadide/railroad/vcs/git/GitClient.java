package dev.railroadide.railroad.vcs.git;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import dev.railroadide.railroad.vcs.git.commit.GitCommitData;
import dev.railroadide.railroad.vcs.git.commit.GitCommitPage;
import dev.railroadide.railroad.vcs.git.commit.GitCommitParser;
import dev.railroadide.railroad.vcs.git.diff.*;
import dev.railroadide.railroad.vcs.git.execution.GitExecutionException;
import dev.railroadide.railroad.vcs.git.execution.GitOutputListener;
import dev.railroadide.railroad.vcs.git.execution.GitProcessRunner;
import dev.railroadide.railroad.vcs.git.execution.GitResult;
import dev.railroadide.railroad.vcs.git.execution.progress.GitProgressEvent;
import dev.railroadide.railroad.vcs.git.execution.progress.GitResultCaptureMode;
import dev.railroadide.railroad.vcs.git.identity.GitAuthor;
import dev.railroadide.railroad.vcs.git.identity.GitIdentity;
import dev.railroadide.railroad.vcs.git.identity.GitSigningStatus;
import dev.railroadide.railroad.vcs.git.remote.GitRemote;
import dev.railroadide.railroad.vcs.git.remote.GitRemoteParser;
import dev.railroadide.railroad.vcs.git.remote.GitUpstream;
import dev.railroadide.railroad.vcs.git.stash.GitStashEntry;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import dev.railroadide.railroad.vcs.git.status.GitRepoStatus;
import dev.railroadide.railroad.vcs.git.status.GitStatusParser;
import dev.railroadide.railroad.vcs.git.util.*;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// TODO: Add small FS cache for detected repositories to avoid repeated git calls
// TODO: Integrate the use of IDE tasks
/**
 * High-level client that executes git operations through a {@link GitProcessRunner}.
 */
public class GitClient {
    private static final Pattern STASH_SUBJECT_PATTERN = Pattern.compile("^(?:WIP on|On)\\s+(.+?):\\s*(.*)$");

    protected final GitProcessRunner runner;

    /**
     * Creates a git client backed by a process runner.
     *
     * @param runner process runner used to execute commands
     */
    public GitClient(GitProcessRunner runner) {
        this.runner = runner;
    }

    /**
     * Updates the git executable used by the runner.
     *
     * @param path git executable path
     */
    public void setGitExecutable(Path path) {
        this.runner.setGitExecutable(path);
    }

    /**
     * Reads repository status from porcelain output.
     *
     * @param repo repository to inspect
     * @return parsed repository status
     * @throws GitExecutionException when command execution fails
     */
    public GitRepoStatus getStatus(GitRepository repo) {
        GitCommand cmd = GitCommands.statusPorcelainV1Z(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.NULL_RECORDS);

        if (result.timedOut())
            throw new GitExecutionException("git status timed out");

        if (result.cancelled())
            throw new GitExecutionException("git status was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git status failed: " + String.join("\n", result.stderr()));

        return GitStatusParser.parsePorcelainV1Z(repo, result.stdout());
    }

    /**
     * Detects repository root for a given path.
     *
     * @param path file or directory path
     * @return detected repository, or empty when not inside a repository
     */
    public Optional<GitRepository> detectRepository(Path path) {
        GitCommand isInsideCmd = GitCommands.revParseIsInsideWorkTree(path);
        GitResult isInsideResult = runner.run(isInsideCmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (isInsideResult.timedOut()) {
            Railroad.LOGGER.warn("git {} timed out for path: {}", isInsideCmd.argsString(), path);
            return Optional.empty();
        }

        if (isInsideResult.cancelled()) {
            Railroad.LOGGER.warn("git {} was cancelled for path: {}", isInsideCmd.argsString(), path);
            return Optional.empty();
        }

        if (isInsideResult.exitCode() != 0 || !"true".equalsIgnoreCase(isInsideResult.readFirstStdoutLine()))
            return Optional.empty();

        GitCommand topLevelCmd = GitCommands.revParseShowTopLevel(path);
        GitResult topLevelResult = runner.run(topLevelCmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (topLevelResult.timedOut()) {
            Railroad.LOGGER.warn("git {} timed out for path: {}", topLevelCmd.argsString(), path);
            return Optional.empty();
        }

        if (topLevelResult.cancelled()) {
            Railroad.LOGGER.warn("git {} was cancelled for path: {}", topLevelCmd.argsString(), path);
            return Optional.empty();
        }

        if (topLevelResult.exitCode() != 0)
            return Optional.empty();

        String topLevelPathStr = String.join("", topLevelResult.stdout()).trim();
        try {
            Path topLevelPath = Path.of(topLevelPathStr).toAbsolutePath().normalize();
            return Optional.of(new GitRepository(topLevelPath));
        } catch (Exception exception) {
            Railroad.LOGGER.warn("Failed to parse git top-level path: {}", topLevelPathStr, exception);
            return Optional.empty();
        }
    }

    /**
     * Commits selected changes and optionally pushes after commit.
     *
     * @param repo repository to commit in
     * @param commit commit details
     * @param pushAfterCommit whether to run push after successful commit
     * @throws GitExecutionException when command execution fails
     */
    public void commitChanges(GitRepository repo, GitCommitData commit, boolean pushAfterCommit) {
        GitCommand commitCmd = GitCommands.commit(repo, commit);
        GitResult commitResult = runner.run(commitCmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (commitResult.timedOut())
            throw new GitExecutionException("git commit timed out");

        if (commitResult.cancelled())
            throw new GitExecutionException("git commit was cancelled");

        if (commitResult.exitCode() != 0)
            throw new GitExecutionException("git commit failed: " + String.join("\n", commitResult.stderr()));

        if (pushAfterCommit) {
            // TODO: Allow passing listeners from higher up
            push(repo, GitOutputListener.NO_OP, event -> {
                if (event instanceof GitProgressEvent.Percentage(String phase, int percent)) {
                    Railroad.LOGGER.debug("Git Push Progress - {}: {}%", phase, percent);
                } else if (event instanceof GitProgressEvent.Message(String message)) {
                    Railroad.LOGGER.debug("Git Push Message - {}", message);
                }
            });
        }
    }

    /**
     * Lists configured remotes.
     *
     * @param repo repository to inspect
     * @return parsed remote entries
     * @throws GitExecutionException when command execution fails
     */
    public List<GitRemote> getRemotes(GitRepository repo) {
        GitCommand cmd = GitCommands.remoteGetUrls(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git remote timed out");

        if (result.cancelled())
            throw new GitExecutionException("git remote was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git remote failed: " + String.join("\n", result.stderr()));

        return GitRemoteParser.parseRemoteUrls(result.stdout());
    }

    /**
     * Reads upstream for the current branch.
     *
     * @param repo repository to inspect
     * @return upstream reference, or empty when unset
     * @throws GitExecutionException when command execution fails
     */
    public Optional<GitUpstream> getUpstream(GitRepository repo) {
        GitCommand cmd = GitCommands.getUpstream(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git rev-parse timed out");

        if (result.cancelled())
            throw new GitExecutionException("git rev-parse was cancelled");

        if (result.exitCode() != 0)
            return Optional.empty();

        String upstreamRef = String.join("", result.stdout()).trim();
        if (upstreamRef.isEmpty())
            return Optional.empty();

        String remoteName;
        String branchName;
        if (upstreamRef.contains("/")) {
            String[] parts = upstreamRef.split("/", 2);
            remoteName = parts[0];
            branchName = parts[1];
        } else {
            remoteName = "origin";
            branchName = upstreamRef;
        }

        return Optional.of(new GitUpstream(remoteName, branchName));
    }

    /**
     * Performs fetch with optional raw output and parsed progress callbacks.
     *
     * @param repo repository to fetch
     * @param rawListener listener for raw output lines
     * @param progressListener consumer for parsed progress events
     * @throws GitExecutionException when command execution fails
     */
    public void fetch(GitRepository repo, GitOutputListener rawListener, Consumer<GitProgressEvent> progressListener) {
        GitCommand cmd = GitCommands.fetch(repo);

        GitOutputListener listener = GitListeners.withProgress(rawListener, progressListener, "Fetch");
        GitResult result = runner.run(cmd, listener, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git fetch timed out");

        if (result.cancelled())
            throw new GitExecutionException("git fetch was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git fetch failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Performs push with optional raw output and parsed progress callbacks.
     *
     * @param repo repository to push
     * @param outputListener listener for raw output lines
     * @param progressListener consumer for parsed progress events
     * @throws GitExecutionException when command execution fails
     */
    public void push(GitRepository repo, GitOutputListener outputListener, Consumer<GitProgressEvent> progressListener) {
        GitCommand cmd = GitCommands.push(repo);

        GitOutputListener listener = GitListeners.withProgress(outputListener, progressListener, "Push");
        GitResult result = runner.run(cmd, listener, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git push timed out");

        if (result.cancelled())
            throw new GitExecutionException("git push was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git push failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Performs pull with optional raw output and parsed progress callbacks.
     *
     * @param repo repository to pull
     * @param outputListener listener for raw output lines
     * @param progressListener consumer for parsed progress events
     * @throws GitExecutionException when command execution fails
     */
    public void pull(GitRepository repo, GitOutputListener outputListener, Consumer<GitProgressEvent> progressListener) {
        GitCommand cmd = GitCommands.pull(repo);

        GitOutputListener listener = GitListeners.withProgress(outputListener, progressListener, "Pull");
        GitResult result = runner.run(cmd, listener, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git pull timed out");

        if (result.cancelled())
            throw new GitExecutionException("git pull was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git pull failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Reads configured git user name.
     *
     * @return user name, or {@code null} when unset
     * @throws GitExecutionException when command execution fails
     */
    public String getUserName() {
        GitCommand cmd = GitCommands.getUserName();
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git config user.name timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config user.name was cancelled");

        if (result.exitCode() != 0)
            return null;

        String userName = String.join("", result.stdout()).trim();
        return userName.isEmpty() ? null : userName;
    }

    /**
     * Reads configured git user email.
     *
     * @return user email, or {@code null} when unset
     * @throws GitExecutionException when command execution fails
     */
    public String getUserEmail() {
        GitCommand cmd = GitCommands.getUserEmail();
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git config user.email timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config user.email was cancelled");

        if (result.exitCode() != 0)
            return null;

        String userEmail = String.join("", result.stdout()).trim();
        return userEmail.isEmpty() ? null : userEmail;
    }

    /**
     * Reads the `commit.gpgsign` setting.
     *
     * @return setting value, or {@code null} when unset
     * @throws GitExecutionException when command execution fails
     */
    public String getCommitGpgSignSetting() {
        GitCommand cmd = GitCommands.getCommitGpgSign();
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git config commit.gpgSign timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config commit.gpgSign was cancelled");

        if (result.exitCode() != 0)
            return null;

        String gpgSign = String.join("", result.stdout()).trim();
        return gpgSign.isEmpty() ? null : gpgSign;
    }

    /**
     * Reads the `gpg.format` setting.
     *
     * @return setting value, or {@code null} when unset
     * @throws GitExecutionException when command execution fails
     */
    public String getGpgFormatSetting() {
        GitCommand cmd = GitCommands.getGpgFormat();
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git config gpg.format timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config gpg.format was cancelled");

        if (result.exitCode() != 0)
            return null;

        String gpgFormat = String.join("", result.stdout()).trim();
        return gpgFormat.isEmpty() ? null : gpgFormat;
    }

    /**
     * Reads configured signing key.
     *
     * @return signing key, or {@code null} when unset
     * @throws GitExecutionException when command execution fails
     */
    public String getUserSigningKey() {
        GitCommand cmd = GitCommands.getUserSigningKey();
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git config user.signingkey timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config user.signingkey was cancelled");

        if (result.exitCode() != 0)
            return null;

        String signingKey = String.join("", result.stdout()).trim();
        return signingKey.isEmpty() ? null : signingKey;
    }

    /**
     * Reads configured gpg program.
     *
     * @return program value, or {@code null} when unset
     * @throws GitExecutionException when command execution fails
     */
    public String getGpgProgramSetting() {
        GitCommand cmd = GitCommands.getGpgProgram();
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git config gpg.program timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config gpg.program was cancelled");

        if (result.exitCode() != 0)
            return null;

        String gpgProgram = String.join("", result.stdout()).trim();
        return gpgProgram.isEmpty() ? null : gpgProgram;
    }

    /**
     * Reads installed git version output.
     *
     * @return git version line, or {@code null} when unavailable
     * @throws GitExecutionException when command execution fails
     */
    public String getGitVersion() {
        GitCommand cmd = GitCommands.getGitVersion();
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git --version timed out");

        if (result.cancelled())
            throw new GitExecutionException("git --version was cancelled");

        if (result.exitCode() != 0)
            return null;

        String versionLine = String.join("", result.stdout()).trim();
        return versionLine.isEmpty() ? null : versionLine;
    }

    /**
     * Builds effective identity information from git config.
     *
     * @return identity snapshot
     */
    public GitIdentity getIdentity() {
        String userName = getUserName();
        String userEmail = getUserEmail();
        String gpgSignSetting = getCommitGpgSignSetting();
        String gpgFormatSetting = getGpgFormatSetting();
        String userSigningKey = getUserSigningKey();
        String gpgProgram = getGpgProgramSetting();

        GitSigningStatus signingStatus = GitSigningStatus.fromGitConfigValues(gpgSignSetting, gpgFormatSetting, userSigningKey, gpgProgram);

        String gitVersion = getGitVersion();

        return new GitIdentity(userName, userEmail, signingStatus, gitVersion);
    }

    /**
     * Retrieves a page of recent commits.
     *
     * @param repo repository to query
     * @param cursor optional page cursor
     * @param limit max number of commits
     * @return parsed commit page
     * @throws GitExecutionException when command execution fails
     */
    public GitCommitPage getRecentCommits(GitRepository repo, @Nullable String cursor, int limit) {
        GitCommand cmd = GitCommands.getRecentCommits(repo, cursor, limit);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git log timed out");

        if (result.cancelled())
            throw new GitExecutionException("git log was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git log failed: " + String.join("\n", result.stderr()));

        return GitCommitParser.parseCommits(result.readAllStdout(), limit);
    }

    /**
     * Retrieves parsed diff for a file change.
     *
     * @param repo repository to query
     * @param change file change descriptor
     * @param mode diff mode
     * @return parsed diff blob
     * @throws GitExecutionException when command execution fails
     */
    public DiffBlob getDiff(GitRepository repo, GitFileChange change, GitDiffMode mode) {
        GitCommand cmd = GitCommands.getDiff(repo, change, mode);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git diff timed out");

        if (result.cancelled())
            throw new GitExecutionException("git diff was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git diff failed: " + String.join("\n", result.stderr()));

        String diffText = result.readAllStdout();
        return DiffParser.parseDiff(diffText);
    }

    /**
     * Retrieves parsed unstaged diff for a path.
     *
     * @param repo repository to query
     * @param filePath path to diff
     * @return parsed diff blob
     * @throws GitExecutionException when command execution fails
     */
    public DiffBlob getUnstagedDiff(GitRepository repo, Path filePath) {
        GitCommand cmd = GitCommands.getUnstagedDiff(repo, filePath);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git diff timed out");

        if (result.cancelled())
            throw new GitExecutionException("git diff was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git diff failed: " + String.join("\n", result.stderr()));

        String diffText = result.readAllStdout();
        return DiffParser.parseDiff(diffText);
    }

    /**
     * Retrieves unstaged diff text for a path.
     *
     * @param repo repository to query
     * @param filePath path to diff
     * @return diff text when available
     */
    public Optional<String> getUnstagedDiffText(GitRepository repo, Path filePath) {
        GitCommand cmd = GitCommands.getUnstagedDiff(repo, filePath);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut()) {
            Railroad.LOGGER.warn("git diff timed out for path: {}", filePath);
            return Optional.empty();
        }

        if (result.cancelled()) {
            Railroad.LOGGER.warn("git diff was cancelled for path: {}", filePath);
            return Optional.empty();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git diff failed for path {}: {}", filePath, String.join("\n", result.stderr()));
            return Optional.empty();
        }

        return Optional.of(result.readAllStdout());
    }

    /**
     * Resolves current HEAD commit hash.
     *
     * @param repo repository to query
     * @return HEAD hash, or {@code null} on failure
     */
    public String getHeadCommitHash(GitRepository repo) {
        GitCommand cmd = GitCommands.getHeadCommitHash(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut()) {
            Railroad.LOGGER.warn("git rev-parse timed out for repository at: {}", repo.root());
            return null;
        }

        if (result.cancelled()) {
            Railroad.LOGGER.warn("git rev-parse was cancelled for repository at: {}", repo.root());
            return null;
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git rev-parse failed for repository at {}: {}", repo.root(), String.join("\n", result.stderr()));
            return null;
        }

        String commitHash = result.readAllStdout().trim();
        return commitHash.isEmpty() ? null : commitHash;
    }

    /**
     * Lists tags pointing at a commit.
     *
     * @param repo repository to query
     * @param hash commit hash
     * @return matching tag names
     */
    public List<String> getTagsPointingToCommit(GitRepository repo, String hash) {
        GitCommand cmd = GitCommands.getTagsPointingToCommit(repo, hash);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut()) {
            Railroad.LOGGER.warn("git tag timed out for repository at: {}", repo.root());
            return List.of();
        }

        if (result.cancelled()) {
            Railroad.LOGGER.warn("git tag was cancelled for repository at: {}", repo.root());
            return List.of();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git tag failed for repository at {}: {}", repo.root(), String.join("\n", result.stderr()));
            return List.of();
        }

        String stdout = result.readAllStdout().trim();
        if (stdout.isEmpty()) {
            return List.of();
        } else {
            return List.of(stdout.split("\n"));
        }
    }

    /**
     * Maps commit hashes to tags that reference them.
     *
     * @param repo repository to query
     * @return map of commit hash to tag names
     */
    public Map<String, List<String>> getTagsByCommit(GitRepository repo) {
        GitCommand cmd = GitCommands.getAllTagsWithCommits(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut()) {
            Railroad.LOGGER.warn("git show-ref timed out for repository at: {}", repo.root());
            return Map.of();
        }

        if (result.cancelled()) {
            Railroad.LOGGER.warn("git show-ref was cancelled for repository at: {}", repo.root());
            return Map.of();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git show-ref failed for repository at {}: {}", repo.root(), String.join("\n", result.stderr()));
            return Map.of();
        }

        String stdout = result.readAllStdout().trim();
        if (stdout.isEmpty())
            return Map.of();

        Map<String, String> tagToCommit = GitCommit.parseTagsToCommit(stdout);

        Map<String, List<String>> tagsByCommit = new HashMap<>();
        for (Map.Entry<String, String> entry : tagToCommit.entrySet()) {
            tagsByCommit.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }

        return tagsByCommit;
    }

    /**
     * Lists all local and remote branch names.
     *
     * @param repo repository to query
     * @return branch names
     */
    public List<String> getAllBranches(GitRepository repo) {
        GitCommand cmd = GitCommands.getAllBranches(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut()) {
            Railroad.LOGGER.warn("git branch timed out for repository at: {}", repo.root());
            return List.of();
        }

        if (result.cancelled()) {
            Railroad.LOGGER.warn("git branch was cancelled for repository at: {}", repo.root());
            return List.of();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git branch failed for repository at {}: {}", repo.root(), String.join("\n", result.stderr()));
            return List.of();
        }

        String stdout = result.readAllStdout().trim();
        if (stdout.isEmpty()) {
            return List.of();
        } else {
            return List.of(stdout.split("\n"));
        }
    }

    /**
     * Lists local branch names.
     *
     * @param repo repository to query
     * @return local branch names
     */
    public List<String> getAllLocalBranches(GitRepository repo) {
        GitCommand cmd = GitCommands.getAllLocalBranches(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut()) {
            Railroad.LOGGER.warn("git branch --list timed out for repository at: {}", repo.root());
            return List.of();
        }

        if (result.cancelled()) {
            Railroad.LOGGER.warn("git branch --list was cancelled for repository at: {}", repo.root());
            return List.of();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git branch --list failed for repository at {}: {}", repo.root(), String.join("\n", result.stderr()));
            return List.of();
        }

        String stdout = result.readAllStdout().trim();
        if (stdout.isEmpty()) {
            return List.of();
        } else {
            return List.of(stdout.split("\n"));
        }
    }

    /**
     * Lists remote branch names.
     *
     * @param repo repository to query
     * @return remote branch names
     */
    public List<String> getAllRemoteBranches(GitRepository repo) {
        GitCommand cmd = GitCommands.getAllRemoteBranches(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut()) {
            Railroad.LOGGER.warn("git branch -r timed out for repository at: {}", repo.root());
            return List.of();
        }

        if (result.cancelled()) {
            Railroad.LOGGER.warn("git branch -r was cancelled for repository at: {}", repo.root());
            return List.of();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git branch -r failed for repository at {}: {}", repo.root(), String.join("\n", result.stderr()));
            return List.of();
        }

        String stdout = result.readAllStdout().trim();
        if (stdout.isEmpty()) {
            return List.of();
        } else {
            return List.of(stdout.split("\n"));
        }
    }

    /**
     * Lists authors for commits in the repository.
     *
     * @param repo repository to query
     * @param includeEmail whether emails should be included
     * @return parsed author entries
     */
    public List<GitAuthor> getAllAuthors(GitRepository repo, boolean includeEmail) {
        GitCommand cmd = GitCommands.getAllAuthors(repo, includeEmail);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut()) {
            Railroad.LOGGER.warn("git shortlog timed out for repository at: {}", repo.root());
            return List.of();
        }

        if (result.cancelled()) {
            Railroad.LOGGER.warn("git shortlog was cancelled for repository at: {}", repo.root());
            return List.of();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git shortlog failed for repository at {}: {}", repo.root(), String.join("\n", result.stderr()));
            return List.of();
        }

        String stdout = result.readAllStdout().trim();
        if (stdout.isEmpty()) {
            return List.of();
        } else {
            String[] lines = stdout.split("\n");
            return GitAuthor.parseAuthorsFromShortlogLines(lines, includeEmail);
        }
    }

    /**
     * Gets repository creation timestamp from the first commit.
     *
     * @param repo repository to query
     * @return epoch-second timestamp, or {@code 0} when unavailable
     */
    public long getRepositoryCreationDate(GitRepository repo) {
        GitCommand cmd = GitCommands.getRepositoryCreationDate(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut()) {
            Railroad.LOGGER.warn("git rev-list timed out for repository at: {}", repo.root());
            return 0L;
        }

        if (result.cancelled()) {
            Railroad.LOGGER.warn("git rev-list was cancelled for repository at: {}", repo.root());
            return 0L;
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git rev-list failed for repository at {}: {}", repo.root(), String.join("\n", result.stderr()));
            return 0L;
        }

        String stdout = result.readAllStdout().trim();
        try {
            return Long.parseLong(stdout);
        } catch (NumberFormatException exception) {
            Railroad.LOGGER.warn("Failed to parse repository creation date '{}' for repository at {}",
                stdout,
                repo.root());
            return 0L;
        }
    }

    /**
     * Gets additions/deletions stats for a commit.
     *
     * @param repo repository to query
     * @param hash commit hash
     * @return parsed per-file stats
     * @throws GitExecutionException when command execution fails
     */
    public List<GitAdditionsDeletions> getAdditionsDeletions(GitRepository repo, String hash) {
        GitCommand cmd = GitCommands.getAdditionsDeletions(repo, hash);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git show timed out");

        if (result.cancelled())
            throw new GitExecutionException("git show was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git show failed: " + String.join("\n", result.stderr()));

        return GitAdditionsDeletionsParser.parseAdditionsDeletions(result.stdout());
    }

    /**
     * Gets full commit message text for a commit.
     *
     * @param repo repository to query
     * @param hash commit hash
     * @return commit message text
     * @throws GitExecutionException when command execution fails
     */
    public String getCommitMessage(GitRepository repo, String hash) {
        GitCommand cmd = GitCommands.getCommitMessage(repo, hash);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git log timed out");

        if (result.cancelled())
            throw new GitExecutionException("git log was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git log failed: " + String.join("\n", result.stderr()));

        return result.readAllStdout();
    }

    /**
     * Creates a stash entry.
     *
     * @param repo repository to modify
     * @param message stash message
     * @param includeUntracked whether untracked files are included
     * @throws GitExecutionException when command execution fails
     */
    public void stashChanges(GitRepository repo, String message, boolean includeUntracked) {
        GitCommand cmd = GitCommands.stashSave(repo, message, includeUntracked);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git stash timed out");

        if (result.cancelled())
            throw new GitExecutionException("git stash was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git stash failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Pops the latest stash.
     *
     * @param repo repository to modify
     * @throws GitExecutionException when command execution fails
     */
    public void stashPop(GitRepository repo) {
        GitCommand cmd = GitCommands.stashPop(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git stash pop timed out");

        if (result.cancelled())
            throw new GitExecutionException("git stash pop was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git stash pop failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Pops a specific stash reference.
     *
     * @param repo repository to modify
     * @param stashRef stash reference
     * @throws GitExecutionException when command execution fails
     */
    public void stashPop(GitRepository repo, String stashRef) {
        GitCommand cmd = GitCommands.stashPop(repo, stashRef);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git stash pop timed out");

        if (result.cancelled())
            throw new GitExecutionException("git stash pop was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git stash pop failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Lists parsed stash entries with derived metadata.
     *
     * @param repo repository to query
     * @return stash entries
     * @throws GitExecutionException when command execution fails
     */
    public List<GitStashEntry> getStashes(GitRepository repo) {
        GitCommand cmd = GitCommands.getStashes(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git stash list timed out");

        if (result.cancelled())
            throw new GitExecutionException("git stash list was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git stash list failed: " + String.join("\n", result.stderr()));

        List<GitStashEntry> stashes = new ArrayList<>();
        for (String line : result.stdout()) {
            if (line == null || line.isBlank())
                continue;

            String[] parts = line.split("\u001F", 4);
            if (parts.length < 4)
                continue;

            long createdAtEpochSeconds = 0L;
            try {
                createdAtEpochSeconds = Long.parseLong(parts[2]);
            } catch (NumberFormatException ignored) {
            }

            String branch = "";
            String message = parts[3];
            Matcher matcher = STASH_SUBJECT_PATTERN.matcher(parts[3]);
            if (matcher.matches()) {
                branch = matcher.group(1).trim();
                message = matcher.group(2).trim();
            }
            if (message.isBlank()) {
                message = parts[3];
            }

            int additions = 0;
            int deletions = 0;
            try {
                List<GitAdditionsDeletions> additionsDeletions = getAdditionsDeletions(repo, parts[0]);
                additions = additionsDeletions.stream().mapToInt(GitAdditionsDeletions::additions).sum();
                deletions = additionsDeletions.stream().mapToInt(GitAdditionsDeletions::deletions).sum();
            } catch (RuntimeException exception) {
                Railroad.LOGGER.debug("Failed to load stash additions/deletions for {}", parts[0], exception);
            }

            stashes.add(new GitStashEntry(parts[0], branch, parts[1], createdAtEpochSeconds, message, additions, deletions));
        }

        return stashes;
    }

    /**
     * Applies a stash without dropping it.
     *
     * @param repo repository to modify
     * @param stashRef stash reference
     * @throws GitExecutionException when command execution fails
     */
    public void stashApply(GitRepository repo, String stashRef) {
        GitCommand cmd = GitCommands.stashApply(repo, stashRef);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git stash apply timed out");

        if (result.cancelled())
            throw new GitExecutionException("git stash apply was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git stash apply failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Drops a stash entry.
     *
     * @param repo repository to modify
     * @param stashRef stash reference
     * @throws GitExecutionException when command execution fails
     */
    public void stashDrop(GitRepository repo, String stashRef) {
        GitCommand cmd = GitCommands.stashDrop(repo, stashRef);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git stash drop timed out");

        if (result.cancelled())
            throw new GitExecutionException("git stash drop was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git stash drop failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Lists file changes represented by a stash entry.
     *
     * @param repo repository to query
     * @param stashRef stash reference
     * @return parsed file changes
     * @throws GitExecutionException when command execution fails
     */
    public List<GitFileChange> getStashChanges(GitRepository repo, String stashRef) {
        GitCommand cmd = GitCommands.getStashChanges(repo, stashRef);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.NULL_RECORDS);

        if (result.timedOut())
            throw new GitExecutionException("git diff --name-status timed out");

        if (result.cancelled())
            throw new GitExecutionException("git diff --name-status was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git diff --name-status failed: " + String.join("\n", result.stderr()));

        List<String> records = result.stdout();
        List<GitFileChange> changes = new ArrayList<>();
        for (int i = 0; i < records.size(); ) {
            String statusToken = records.get(i++);
            if (statusToken == null || statusToken.isBlank())
                continue;

            char status = statusToken.charAt(0);
            if (status == 'R' || status == 'C') {
                if (i + 1 >= records.size())
                    break;

                String oldPath = records.get(i++);
                String newPath = records.get(i++);
                if (newPath == null || newPath.isBlank())
                    continue;

                changes.add(new GitFileChange(
                    repo.root().resolve(newPath).normalize(),
                    oldPath == null || oldPath.isBlank() ? null : repo.root().resolve(oldPath).normalize(),
                    status,
                    ' '
                ));
                continue;
            }

            if (i >= records.size())
                break;

            String path = records.get(i++);
            if (path == null || path.isBlank())
                continue;

            changes.add(new GitFileChange(
                repo.root().resolve(path).normalize(),
                status,
                ' '
            ));
        }

        return changes;
    }

    /**
     * Gets stash diff text for a file path.
     *
     * @param repo repository to query
     * @param stashRef stash reference
     * @param filePath file path to diff
     * @return diff text when available
     */
    public Optional<String> getStashDiffText(GitRepository repo, String stashRef, Path filePath) {
        GitCommand cmd = GitCommands.getStashDiff(repo, stashRef, filePath);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut()) {
            Railroad.LOGGER.warn("git stash diff timed out for {} in {}", filePath, stashRef);
            return Optional.empty();
        }

        if (result.cancelled()) {
            Railroad.LOGGER.warn("git stash diff was cancelled for {} in {}", filePath, stashRef);
            return Optional.empty();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git stash diff failed for {} in {}: {}", filePath, stashRef, String.join("\n", result.stderr()));
            return Optional.empty();
        }

        return Optional.ofNullable(result.readAllStdout());
    }

    /**
     * Checks out a commit in detached mode.
     *
     * @param repo repository to modify
     * @param hash commit hash
     * @param gitVersion git version string used to select command variant
     * @throws GitExecutionException when command execution fails
     */
    public void checkoutCommit(GitRepository repo, String hash, String gitVersion) {
        GitCommand cmd = supportsSwitch(gitVersion)
            ? GitCommands.checkoutDetachedWithSwitch(repo, hash)
            : GitCommands.checkoutDetached(repo, hash);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git checkout timed out");

        if (result.cancelled())
            throw new GitExecutionException("git checkout was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git checkout failed: " + String.join("\n", result.stderr()));
    }

    private static boolean supportsSwitch(String gitVersion) {
        if (gitVersion == null || gitVersion.isEmpty())
            return false;

        String[] parts = gitVersion.split(" ");
        if (parts.length < 3)
            return false;

        String versionStr = parts[2];
        String[] versionParts = versionStr.split("\\.");
        if (versionParts.length < 2)
            return false;

        try {
            int major = Integer.parseInt(versionParts[0]);
            int minor = Integer.parseInt(versionParts[1]);

            // Assume support for versions 2.23 and above
            return (major > 2) || (major == 2 && minor >= 23);
        } catch (NumberFormatException exception) {
            Railroad.LOGGER.warn("Failed to parse git version numbers from: {}", versionStr, exception);
            return false;
        }
    }

    /**
     * Performs `git reset --hard`.
     *
     * @param repo repository to modify
     * @throws GitExecutionException when command execution fails
     */
    public void resetHard(GitRepository repo) {
        GitCommand cmd = GitCommands.resetHard(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git reset --hard timed out");

        if (result.cancelled())
            throw new GitExecutionException("git reset --hard was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git reset --hard failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Removes untracked files and directories.
     *
     * @param repo repository to modify
     * @throws GitExecutionException when command execution fails
     */
    public void cleanUntrackedFiles(GitRepository repo) {
        GitCommand cmd = GitCommands.cleanUntrackedFiles(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git clean timed out");

        if (result.cancelled())
            throw new GitExecutionException("git clean was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git clean failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Gets the current commit.
     *
     * @param repo repository to query
     * @return latest commit, or {@code null} when unavailable
     */
    public GitCommit getCurrentCommit(GitRepository repo) {
        List<GitCommit> commits = getRecentCommits(repo, null, 1).commits();
        return commits.isEmpty() ? null : commits.getFirst();
    }

    /**
     * Validates a branch name.
     *
     * @param repo repository to query
     * @param string branch name candidate
     * @return {@code true} when the name is valid
     * @throws GitExecutionException when command execution fails
     */
    public boolean isValidBranchName(GitRepository repo, String string) {
        GitCommand cmd = GitCommands.checkValidBranchName(repo, string);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git check-ref-format timed out");

        if (result.cancelled())
            throw new GitExecutionException("git check-ref-format was cancelled");

        return result.exitCode() == 0;
    }

    /**
     * Creates a branch at the given hash.
     *
     * @param repo repository to modify
     * @param branchName branch name
     * @param hash start-point hash
     * @throws GitExecutionException when command execution fails
     */
    public void createBranch(GitRepository repo, String branchName, String hash) {
        GitCommand cmd = GitCommands.createBranch(repo, branchName, hash);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git branch timed out");

        if (result.cancelled())
            throw new GitExecutionException("git branch was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git branch failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Checks out a branch using command variant supported by git version.
     *
     * @param repository repository to modify
     * @param branchName branch name
     * @param gitVersion git version string
     * @throws GitExecutionException when command execution fails
     */
    public void checkoutBranch(GitRepository repository, String branchName, String gitVersion) {
        GitCommand cmd = supportsSwitch(gitVersion)
            ? GitCommands.checkoutBranchWithSwitch(repository, branchName)
            : GitCommands.checkoutBranch(repository, branchName);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git checkout timed out");

        if (result.cancelled())
            throw new GitExecutionException("git checkout was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git checkout failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Checks whether a tag exists.
     *
     * @param repo repository to query
     * @param tagName tag name
     * @return {@code true} when the tag exists
     * @throws GitExecutionException when command execution fails
     */
    public boolean doesTagExist(GitRepository repo, String tagName) {
        GitCommand cmd = GitCommands.checkTagExists(repo, tagName);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git rev-parse timed out");

        if (result.cancelled())
            throw new GitExecutionException("git rev-parse was cancelled");

        return result.exitCode() == 0;
    }

    /**
     * Validates a tag name.
     *
     * @param repo repository to query
     * @param tagName tag name candidate
     * @return {@code true} when the name is valid
     * @throws GitExecutionException when command execution fails
     */
    public boolean isValidTagName(GitRepository repo, String tagName) {
        GitCommand cmd = GitCommands.checkValidTagName(repo, tagName);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git check-ref-format timed out");

        if (result.cancelled())
            throw new GitExecutionException("git check-ref-format was cancelled");

        return result.exitCode() == 0;
    }

    /**
     * Creates or updates a tag.
     *
     * @param repo repository to modify
     * @param tagName tag name
     * @param hash target hash
     * @param message optional annotation message
     * @param overwrite whether overwrite is allowed
     * @throws GitExecutionException when command execution fails
     */
    public void createTag(GitRepository repo, String tagName, String hash, @Nullable String message, boolean overwrite) {
        GitCommand cmd = GitCommands.createTag(repo, tagName, hash, message, overwrite);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git tag timed out");

        if (result.cancelled())
            throw new GitExecutionException("git tag was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git tag failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Checks whether repository is currently in cherry-pick state.
     *
     * @param repo repository to query
     * @return {@code true} when cherry-pick metadata is present
     * @throws GitExecutionException when command execution fails
     */
    public boolean isInCherryPickState(GitRepository repo) {
        GitCommand cmd = GitCommands.checkCherryPickState(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git rev-parse timed out");

        if (result.cancelled())
            throw new GitExecutionException("git rev-parse was cancelled");

        return result.exitCode() == 0;
    }

    /**
     * Cherry-picks a commit and classifies result.
     *
     * @param repo repository to modify
     * @param commitHash commit hash to cherry-pick
     * @return cherry-pick result classification
     * @throws GitExecutionException when command execution fails unexpectedly
     */
    public CherryPickResult cherryPickCommit(GitRepository repo, String commitHash) {
        GitCommand cmd = GitCommands.cherryPickCommit(repo, commitHash);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git cherry-pick timed out");

        if (result.cancelled())
            throw new GitExecutionException("git cherry-pick was cancelled");

        if (result.exitCode() == 0) {
            return CherryPickResult.SUCCESS;
        } else {
            String stderr = String.join("\n", result.stderr());
            if (stderr.contains("could not apply")) {
                return CherryPickResult.CONFLICTS;
            } else {
                throw new GitExecutionException("git cherry-pick failed: " + stderr);
            }
        }
    }

    /**
     * Continues a paused cherry-pick operation.
     *
     * @param repo repository to modify
     * @throws GitExecutionException when command execution fails
     */
    public void continueCherryPick(GitRepository repo) {
        GitCommand cmd = GitCommands.continueCherryPick(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git cherry-pick --continue timed out");

        if (result.cancelled())
            throw new GitExecutionException("git cherry-pick --continue was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git cherry-pick --continue failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Aborts a cherry-pick operation.
     *
     * @param repo repository to modify
     * @throws GitExecutionException when command execution fails
     */
    public void abortCherryPick(GitRepository repo) {
        GitCommand cmd = GitCommands.abortCherryPick(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git cherry-pick --abort timed out");

        if (result.cancelled())
            throw new GitExecutionException("git cherry-pick --abort was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git cherry-pick --abort failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Quits cherry-pick state.
     *
     * @param repo repository to modify
     * @throws GitExecutionException when command execution fails
     */
    public void quitCherryPick(GitRepository repo) {
        GitCommand cmd = GitCommands.quitCherryPick(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git cherry-pick --quit timed out");

        if (result.cancelled())
            throw new GitExecutionException("git cherry-pick --quit was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git cherry-pick --quit failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Reverts a commit.
     *
     * @param repo repository to modify
     * @param commitHash commit hash to revert
     * @throws GitExecutionException when command execution fails
     */
    public void revertCommit(GitRepository repo, String commitHash) {
        GitCommand cmd = GitCommands.revertCommit(repo, commitHash);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git revert timed out");

        if (result.cancelled())
            throw new GitExecutionException("git revert was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git revert failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Resolves upstream branch reference for a local branch.
     *
     * @param repo repository to query
     * @param branchName local branch name
     * @return upstream reference, or {@code null} when none is configured
     * @throws GitExecutionException when command execution fails
     */
    public String getRemoteTrackingBranch(GitRepository repo, String branchName) {
        GitCommand cmd = GitCommands.getRemoteTrackingBranch(repo, branchName);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git rev-parse timed out");

        if (result.cancelled())
            throw new GitExecutionException("git rev-parse was cancelled");

        if (result.exitCode() != 0)
            return null;

        String upstreamRef = result.readAllStdout().trim();
        return upstreamRef.isEmpty() ? null : upstreamRef;
    }

    /**
     * Computes ahead/behind counts between two branches.
     *
     * @param repo repository to query
     * @param branchName local branch
     * @param upstreamBranch upstream branch
     * @return two-element array: `[ahead, behind]`
     * @throws GitExecutionException when command execution fails
     */
    public int[] getAheadBehindCounts(GitRepository repo, String branchName, String upstreamBranch) {
        GitCommand cmd = GitCommands.getAheadBehindCount(repo, branchName, upstreamBranch);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git rev-list timed out");

        if (result.cancelled())
            throw new GitExecutionException("git rev-list was cancelled");

        if (result.exitCode() != 0)
            return new int[]{0, 0};

        String stdout = result.readAllStdout().strip();
        try {
            String[] parts = stdout.split("\\s+");
            if (parts.length != 2) {
                Railroad.LOGGER.warn("Unexpected ahead-behind output '{}' for branch {} in repository at {}",
                    stdout, branchName, repo.root());
                return new int[]{0, 0};
            }

            int aheadCount = Integer.parseInt(parts[0]);
            int behindCount = Integer.parseInt(parts[1]);
            return new int[]{aheadCount, behindCount};
        } catch (NumberFormatException exception) {
            Railroad.LOGGER.warn("Failed to parse ahead-behind count '{}' for branch {} in repository at {}",
                stdout, branchName, repo.root(), exception);
            return new int[]{0, 0};
        }
    }

    /**
     * Resolves latest commit hash for a branch.
     *
     * @param repo repository to query
     * @param branchName branch name
     * @return commit hash, or {@code null} when unavailable
     * @throws GitExecutionException when command execution fails
     */
    public String getLastCommitHash(GitRepository repo, String branchName) {
        GitCommand cmd = GitCommands.getLastCommitHash(repo, branchName);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git rev-parse timed out");

        if (result.cancelled())
            throw new GitExecutionException("git rev-parse was cancelled");

        if (result.exitCode() != 0)
            return null;

        String commitHash = result.readAllStdout().trim();
        return commitHash.isEmpty() ? null : commitHash;
    }

    /**
     * Resolves latest commit timestamp for a branch.
     *
     * @param repo repository to query
     * @param branchName branch name
     * @return epoch-second timestamp, or {@code null} when unavailable
     * @throws GitExecutionException when command execution fails
     */
    public Long getLastCommitTimestamp(GitRepository repo, String branchName) {
        GitCommand cmd = GitCommands.getLastCommitTimestamp(repo, branchName);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git log timed out");

        if (result.cancelled())
            throw new GitExecutionException("git log was cancelled");

        if (result.exitCode() != 0)
            return null;

        String timestamp = result.readAllStdout().trim();
        if (timestamp.isEmpty())
            return null;

        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            Railroad.LOGGER.warn("Failed to parse last commit timestamp '{}' for branch {} in repository at {}",
                timestamp, branchName, repo.root(), exception);
            return null;
        }
    }

    /**
     * Reads commit author name/email for a commit hash.
     *
     * @param repo repository to query
     * @param hash commit hash
     * @return author record, or {@code null} when unavailable
     * @throws GitExecutionException when command execution fails
     */
    public GitAuthor getCommitAuthor(GitRepository repo, String hash) {
        GitCommand cmd = GitCommands.getCommitAuthor(repo, hash);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git log timed out");

        if (result.cancelled())
            throw new GitExecutionException("git log was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git log failed: " + String.join("\n", result.stderr()));

        String stdout = result.readAllStdout();
        if (stdout.isBlank())
            return null;

        String[] parts = stdout.split("\u0000", -1);
        String authorName = parts.length > 0 ? parts[0].trim() : null;
        String authorEmail = parts.length > 1 ? parts[1].trim() : null;
        if (authorName != null && authorName.isBlank()) {
            authorName = null;
        }
        if (authorEmail != null && authorEmail.isBlank()) {
            authorEmail = null;
        }

        return authorName == null ? null : new GitAuthor(0, authorName, authorEmail);
    }

    /**
     * Indicates whether a branch has uncommitted changes.
     *
     * @param repo repository to query
     * @param branchName branch name
     * @return currently always {@code false}
     */
    public boolean hasUncommittedChanges(GitRepository repo, String branchName) {
        return false;
    }

    /**
     * Sets upstream branch for a local branch.
     *
     * @param repo repository to modify
     * @param branchName local branch name
     * @param upstreamBranch upstream branch name
     * @throws GitExecutionException when command execution fails
     */
    public void setBranchUpstream(GitRepository repo, String branchName, String upstreamBranch) {
        GitCommand cmd = GitCommands.setBranchUpstream(repo, branchName, upstreamBranch);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git branch --set-upstream-to timed out");

        if (result.cancelled())
            throw new GitExecutionException("git branch --set-upstream-to was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git branch --set-upstream-to failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Unsets upstream branch for a local branch.
     *
     * @param repo repository to modify
     * @param branchName local branch name
     * @throws GitExecutionException when command execution fails
     */
    public void unsetBranchUpstream(GitRepository repo, String branchName) {
        GitCommand cmd = GitCommands.unsetBranchUpstream(repo, branchName);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git branch --unset-upstream timed out");

        if (result.cancelled())
            throw new GitExecutionException("git branch --unset-upstream was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git branch --unset-upstream failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Deletes a branch.
     *
     * @param repo repository to modify
     * @param branchName branch name
     * @param force whether deletion should be forced
     * @throws GitExecutionException when command execution fails
     */
    public void deleteBranch(GitRepository repo, String branchName, boolean force) {
        GitCommand cmd = GitCommands.deleteBranch(repo, branchName, force);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git branch -d timed out");

        if (result.cancelled())
            throw new GitExecutionException("git branch -d was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git branch -d failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Renames a branch.
     *
     * @param repo repository to modify
     * @param oldBranchName existing branch name
     * @param newBranchName new branch name
     * @param force whether rename should be forced
     * @throws GitExecutionException when command execution fails
     */
    public void renameBranch(GitRepository repo, String oldBranchName, String newBranchName, boolean force) {
        GitCommand cmd = GitCommands.renameBranch(repo, oldBranchName, newBranchName, force);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git branch -m timed out");

        if (result.cancelled())
            throw new GitExecutionException("git branch -m was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git branch -m failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Lists URLs configured for a remote.
     *
     * @param repo repository to query
     * @param remote remote descriptor
     * @return configured remote URLs
     * @throws GitExecutionException when command execution fails
     */
    public List<String> getRemoteUrls(GitRepository repo, GitRemote remote) {
        GitCommand cmd = GitCommands.getRemoteUrls(repo, remote);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git remote get-url timed out");

        if (result.cancelled())
            throw new GitExecutionException("git remote get-url was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git remote get-url failed: " + String.join("\n", result.stderr()));

        String stdout = result.readAllStdout().trim();
        if (stdout.isEmpty()) {
            return List.of();
        } else {
            return List.of(stdout.split("\n"));
        }
    }

    /**
     * Adds a remote and optionally sets a distinct push URL.
     *
     * @param repo repository to modify
     * @param name remote name
     * @param fetchUrl fetch URL
     * @param pushUrl push URL
     * @throws GitExecutionException when command execution fails
     */
    public void addRemote(GitRepository repo, String name, String fetchUrl, String pushUrl) {
        GitCommand addRemote = GitCommands.addRemote(repo, name, fetchUrl);
        GitResult addRemoteResult = runner.run(addRemote, null, null, GitResultCaptureMode.TEXT_LINES);

        if (addRemoteResult.timedOut())
            throw new GitExecutionException("git remote add timed out");

        if (addRemoteResult.cancelled())
            throw new GitExecutionException("git remote add was cancelled");

        if (addRemoteResult.exitCode() != 0)
            throw new GitExecutionException("git remote add failed: " + String.join("\n", addRemoteResult.stderr()));

        if (!fetchUrl.equals(pushUrl)) {
            setRemotePushUrl(repo, name, pushUrl);
        }
    }

    /**
     * Updates remote name and URLs.
     *
     * @param repo repository to modify
     * @param oldName existing remote name
     * @param newName new remote name
     * @param fetchUrl fetch URL
     * @param pushUrl push URL
     * @throws GitExecutionException when command execution fails
     */
    public void updateRemote(GitRepository repo, String oldName, String newName, String fetchUrl, String pushUrl) {
        String effectiveName = oldName;
        if (!oldName.equals(newName)) {
            GitCommand renameRemote = GitCommands.renameRemote(repo, oldName, newName);
            GitResult renameRemoteResult = runner.run(renameRemote, null, null, GitResultCaptureMode.TEXT_LINES);

            if (renameRemoteResult.timedOut())
                throw new GitExecutionException("git remote rename timed out");

            if (renameRemoteResult.cancelled())
                throw new GitExecutionException("git remote rename was cancelled");

            if (renameRemoteResult.exitCode() != 0)
                throw new GitExecutionException("git remote rename failed: " + String.join("\n", renameRemoteResult.stderr()));

            effectiveName = newName;
        }

        setRemoteFetchUrl(repo, effectiveName, fetchUrl);
        setRemotePushUrl(repo, effectiveName, pushUrl);
    }

    /**
     * Removes a remote.
     *
     * @param repo repository to modify
     * @param name remote name
     * @throws GitExecutionException when command execution fails
     */
    public void removeRemote(GitRepository repo, String name) {
        GitCommand removeRemote = GitCommands.removeRemote(repo, name);
        GitResult removeRemoteResult = runner.run(removeRemote, null, null, GitResultCaptureMode.TEXT_LINES);

        if (removeRemoteResult.timedOut())
            throw new GitExecutionException("git remote remove timed out");

        if (removeRemoteResult.cancelled())
            throw new GitExecutionException("git remote remove was cancelled");

        if (removeRemoteResult.exitCode() != 0)
            throw new GitExecutionException("git remote remove failed: " + String.join("\n", removeRemoteResult.stderr()));
    }

    private void setRemoteFetchUrl(GitRepository repo, String name, String fetchUrl) {
        GitCommand setFetchUrl = GitCommands.setRemoteFetchUrl(repo, name, fetchUrl);
        GitResult setFetchUrlResult = runner.run(setFetchUrl, null, null, GitResultCaptureMode.TEXT_LINES);

        if (setFetchUrlResult.timedOut())
            throw new GitExecutionException("git remote set-url timed out");

        if (setFetchUrlResult.cancelled())
            throw new GitExecutionException("git remote set-url was cancelled");

        if (setFetchUrlResult.exitCode() != 0)
            throw new GitExecutionException("git remote set-url failed: " + String.join("\n", setFetchUrlResult.stderr()));
    }

    private void setRemotePushUrl(GitRepository repo, String name, String pushUrl) {
        GitCommand setPushUrl = GitCommands.setRemotePushUrl(repo, name, pushUrl);
        GitResult setPushUrlResult = runner.run(setPushUrl, null, null, GitResultCaptureMode.TEXT_LINES);

        if (setPushUrlResult.timedOut())
            throw new GitExecutionException("git remote set-url --push timed out");

        if (setPushUrlResult.cancelled())
            throw new GitExecutionException("git remote set-url --push was cancelled");

        if (setPushUrlResult.exitCode() != 0)
            throw new GitExecutionException("git remote set-url --push failed: " + String.join("\n", setPushUrlResult.stderr()));
    }

    /**
     * Reads whether prune is enabled for a remote.
     *
     * @param repo repository to query
     * @param remote remote descriptor
     * @return {@code true} when prune is enabled
     * @throws GitExecutionException when command execution fails
     */
    public boolean isPruningEnabled(GitRepository repo, GitRemote remote) {
        GitCommand cmd = GitCommands.isPruningEnabled(repo, remote);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git config timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git config failed: " + String.join("\n", result.stderr()));

        String stdout = result.readAllStdout().trim();
        return stdout.equalsIgnoreCase("true");
    }

    /**
     * Fetches all remotes with progress callbacks.
     *
     * @param repo repository to modify
     * @param rawListener raw output listener
     * @param progressListener parsed progress listener
     * @throws GitExecutionException when command execution fails
     */
    public void fetchAllRemotes(GitRepository repo, GitOutputListener rawListener, Consumer<GitProgressEvent> progressListener) {
        GitCommand cmd = GitCommands.fetchAllRemotes(repo);

        GitOutputListener listener = GitListeners.withProgress(rawListener, progressListener, "Fetch");
        GitResult result = runner.run(cmd, listener, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git fetch timed out");

        if (result.cancelled())
            throw new GitExecutionException("git fetch was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git fetch failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Prunes all remotes with progress callbacks.
     *
     * @param repo repository to modify
     * @param rawListener raw output listener
     * @param progressListener parsed progress listener
     * @throws GitExecutionException when command execution fails
     */
    public void pruneAllRemotes(GitRepository repo, GitOutputListener rawListener, Consumer<GitProgressEvent> progressListener) {
        GitCommand cmd = GitCommands.pruneAllRemotes(repo);

        GitOutputListener listener = GitListeners.withProgress(rawListener, progressListener, "Prune");
        GitResult result = runner.run(cmd, listener, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git remote prune timed out");

        if (result.cancelled())
            throw new GitExecutionException("git remote prune was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git remote prune failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Runs git garbage collection with progress callbacks.
     *
     * @param repo repository to modify
     * @param rawListener raw output listener
     * @param progressListener parsed progress listener
     * @throws GitExecutionException when command execution fails
     */
    public void gc(GitRepository repo, GitOutputListener rawListener, Consumer<GitProgressEvent> progressListener) {
        GitCommand cmd = GitCommands.gc(repo);

        GitOutputListener listener = GitListeners.withProgress(rawListener, progressListener, "Prune");
        GitResult result = runner.run(cmd, listener, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git gc timed out");

        if (result.cancelled())
            throw new GitExecutionException("git gc was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git gc failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Reads whether pull is configured as fast-forward only.
     *
     * @param repo repository to query
     * @return {@code true} when fast-forward only is enabled
     * @throws GitExecutionException when command execution fails
     */
    public boolean isPullFastForwardOnly(GitRepository repo) {
        GitCommand cmd = GitCommands.isPullFastForwardOnly(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git config timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config was cancelled");

        if (result.exitCode() != 0)
            return false;

        String stdout = result.readAllStdout().trim();
        return stdout.equalsIgnoreCase("true") || stdout.equalsIgnoreCase("only");
    }

    /**
     * Resolves effective pull strategy for a branch.
     *
     * @param repo repository to query
     * @param currentBranch current branch name
     * @return effective pull strategy
     * @throws GitExecutionException when command execution fails
     */
    public GitPullStrategy getPullStrategy(GitRepository repo, String currentBranch) {
        boolean isPullFastForwardOnly = isPullFastForwardOnly(repo);
        if (isPullFastForwardOnly)
            return GitPullStrategy.FAST_FORWARD_ONLY;

        GitRebaseSetting rebaseSetting = getBranchRebaseSetting(repo, currentBranch);
        if (rebaseSetting != GitRebaseSetting.UNSET)
            return rebaseSetting == GitRebaseSetting.REBASE
                ? GitPullStrategy.REBASE
                : GitPullStrategy.MERGE;

        GitRebaseSetting globalRebaseSetting = getGlobalRebaseSetting(repo);
        if (globalRebaseSetting != GitRebaseSetting.UNSET)
            return globalRebaseSetting == GitRebaseSetting.REBASE
                ? GitPullStrategy.REBASE
                : GitPullStrategy.MERGE;

        return GitPullStrategy.MERGE;
    }

    private GitRebaseSetting getGlobalRebaseSetting(GitRepository repo) {
        GitCommand cmd = GitCommands.getGlobalRebaseSetting(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git config timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config was cancelled");

        if (result.exitCode() != 0)
            return GitRebaseSetting.UNSET;

        String stdout = result.readAllStdout().trim();
        return switch (stdout) {
            case "true", "rebase" -> GitRebaseSetting.REBASE;
            case "false", "merge" -> GitRebaseSetting.MERGE;
            case "merges" -> GitRebaseSetting.MERGES;
            default -> GitRebaseSetting.UNSET;
        };
    }

    private GitRebaseSetting getBranchRebaseSetting(GitRepository repo, String currentBranch) {
        GitCommand cmd = GitCommands.getBranchRebaseSetting(repo, currentBranch);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git config timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config was cancelled");

        if (result.exitCode() != 0)
            return GitRebaseSetting.UNSET;

        String stdout = result.readAllStdout().trim();
        return switch (stdout) {
            case "true", "rebase" -> GitRebaseSetting.REBASE;
            case "false", "merge" -> GitRebaseSetting.MERGE;
            case "merges" -> GitRebaseSetting.MERGES;
            default -> GitRebaseSetting.UNSET;
        };
    }

    /**
     * Reads configured push strategy.
     *
     * @param repo repository to query
     * @return configured push strategy, defaulting to {@link GitPushStrategy#SIMPLE}
     * @throws GitExecutionException when command execution fails
     */
    public GitPushStrategy getPushStrategy(GitRepository repo) {
        GitCommand cmd = GitCommands.getPushDefault(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut())
            throw new GitExecutionException("git config timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config was cancelled");

        if (result.exitCode() != 0)
            return GitPushStrategy.SIMPLE; // Default push strategy in Git

        String stdout = result.readAllStdout().trim();
        return switch (stdout) {
            case "simple" -> GitPushStrategy.SIMPLE;
            case "current" -> GitPushStrategy.CURRENT;
            case "upstream" -> GitPushStrategy.UPSTREAM;
            case "matching" -> GitPushStrategy.MATCHING;
            case "nothing" -> GitPushStrategy.NOTHING;
            default -> GitPushStrategy.SIMPLE; // Fallback to simple if unknown value
        };
    }

    /**
     * Updates push strategy configuration.
     *
     * @param repo repository to modify
     * @param strategy push strategy
     * @param branchName branch name associated with caller context
     * @throws GitExecutionException when command execution fails
     */
    public void setPushStrategy(GitRepository repo, GitPushStrategy strategy, String branchName) {
        GitCommand cmd;
        if (strategy == GitPushStrategy.SIMPLE) {
            // Unset push.default to revert to Git's default behavior
            cmd = GitCommands.unsetPushDefault(repo);
        } else {
            cmd = GitCommands.setPushDefault(repo, strategy);
        }

        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git config timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git config failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Updates pull strategy configuration.
     *
     * @param repository repository to modify
     * @param strategy pull strategy
     * @throws GitExecutionException when command execution fails
     */
    public void setPullStrategy(GitRepository repository, GitPullStrategy strategy) {
        GitCommand cmd;
        if (strategy == GitPullStrategy.MERGE) {
            // Unset rebase and pull.ff to revert to Git's default behavior
            cmd = GitCommands.unsetPullRebase(repository);
        } else if (strategy == GitPullStrategy.REBASE) {
            cmd = GitCommands.setPullRebase(repository, true);
        } else if (strategy == GitPullStrategy.FAST_FORWARD_ONLY) {
            cmd = GitCommands.setPullFastForwardOnly(repository, true);
        } else {
            throw new IllegalArgumentException("Unsupported pull strategy: " + strategy);
        }

        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git config timed out");

        if (result.cancelled())
            throw new GitExecutionException("git config was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git config failed: " + String.join("\n", result.stderr()));
    }

    /**
     * Lists commits reachable from {@code branchB} and not from {@code branchA}.
     *
     * @param repo repository to query
     * @param branchA left-side reference
     * @param branchB right-side reference
     * @return parsed commits in range
     * @throws GitExecutionException when command execution fails
     */
    public List<GitCommit> getCommitsBetween(GitRepository repo, String branchA, String branchB) {
        GitCommand cmd = GitCommands.getCommitsBetween(repo, branchA, branchB);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut())
            throw new GitExecutionException("git log timed out");

        if (result.cancelled())
            throw new GitExecutionException("git log was cancelled");

        if (result.exitCode() != 0)
            throw new GitExecutionException("git log failed: " + String.join("\n", result.stderr()));

        return GitCommitParser.parseCommits(result.readAllStdout(), Integer.MAX_VALUE).commits();
    }
}
