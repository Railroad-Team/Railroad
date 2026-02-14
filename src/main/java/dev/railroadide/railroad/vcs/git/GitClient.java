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
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import dev.railroadide.railroad.vcs.git.status.GitRepoStatus;
import dev.railroadide.railroad.vcs.git.status.GitStatusParser;
import dev.railroadide.railroad.vcs.git.util.CherryPickResult;
import dev.railroadide.railroad.vcs.git.util.GitRepository;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

// TODO: Add small FS cache for detected repositories to avoid repeated git calls
// TODO: Integrate the use of IDE tasks
public class GitClient {
    protected final GitProcessRunner runner;

    public GitClient(GitProcessRunner runner) {
        this.runner = runner;
    }

    public void setGitExecutable(Path path) {
        this.runner.setGitExecutable(path);
    }

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

    public GitCommit getCurrentCommit(GitRepository repo) {
        List<GitCommit> commits = getRecentCommits(repo, null, 1).commits();
        return commits.isEmpty() ? null : commits.getFirst();
    }

    public boolean isValidBranchName(GitRepository repo, String string) {
        GitCommand cmd = GitCommands.checkValidBranchName(repo, string);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git check-ref-format timed out");

        if (result.cancelled())
            throw new GitExecutionException("git check-ref-format was cancelled");

        return result.exitCode() == 0;
    }

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

    public boolean doesTagExist(GitRepository repo, String tagName) {
        GitCommand cmd = GitCommands.checkTagExists(repo, tagName);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git rev-parse timed out");

        if (result.cancelled())
            throw new GitExecutionException("git rev-parse was cancelled");

        return result.exitCode() == 0;
    }

    public boolean isValidTagName(GitRepository repo, String tagName) {
        GitCommand cmd = GitCommands.checkValidTagName(repo, tagName);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git check-ref-format timed out");

        if (result.cancelled())
            throw new GitExecutionException("git check-ref-format was cancelled");

        return result.exitCode() == 0;
    }

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

    public boolean isInCherryPickState(GitRepository repo) {
        GitCommand cmd = GitCommands.checkCherryPickState(repo);
        GitResult result = runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);
        if (result.timedOut())
            throw new GitExecutionException("git rev-parse timed out");

        if (result.cancelled())
            throw new GitExecutionException("git rev-parse was cancelled");

        return result.exitCode() == 0;
    }

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

    public boolean hasUncommittedChanges(GitRepository repo, String branchName) {
        return false;
    }

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
}
