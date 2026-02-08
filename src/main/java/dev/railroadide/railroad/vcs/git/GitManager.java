package dev.railroadide.railroad.vcs.git;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.project.data.ProjectDataStore;
import dev.railroadide.railroad.vcs.git.commit.CommitListMetadata;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import dev.railroadide.railroad.vcs.git.commit.GitCommitData;
import dev.railroadide.railroad.vcs.git.commit.GitCommitPage;
import dev.railroadide.railroad.vcs.git.diff.GitAdditionsDeletions;
import dev.railroadide.railroad.vcs.git.execution.GitOutputListener;
import dev.railroadide.railroad.vcs.git.execution.GitResult;
import dev.railroadide.railroad.vcs.git.execution.progress.GitProgressEvent;
import dev.railroadide.railroad.vcs.git.execution.progress.GitResultCaptureMode;
import dev.railroadide.railroad.vcs.git.identity.GitIdentity;
import dev.railroadide.railroad.vcs.git.remote.GitRemote;
import dev.railroadide.railroad.vcs.git.remote.GitUpstream;
import dev.railroadide.railroad.vcs.git.status.GitRepoStatus;
import dev.railroadide.railroad.vcs.git.util.CherryPickResult;
import dev.railroadide.railroad.vcs.git.util.GitRepository;
import dev.railroadide.railroad.vcs.git.util.GitSettings;
import javafx.beans.property.*;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class GitManager {
    private static final String SETTINGS_PATH = "vcs/git.json";
    private static final long DEFAULT_AUTO_REFRESH_INTERVAL_MILLIS = 5000L;

    private final Project project;
    private final GitClient gitClient;

    private final ScheduledExecutorService executorService;

    private final ObjectProperty<GitRepoStatus> repoStatus = new SimpleObjectProperty<>();
    private final BooleanProperty active = new SimpleBooleanProperty(false);
    private final ObjectProperty<GitRepository> gitRepository = new SimpleObjectProperty<>();
    private final LongProperty lastFetchTimestamp = new SimpleLongProperty(0L);
    private final ObjectProperty<GitIdentity> gitIdentity = new SimpleObjectProperty<>();
    private final LongProperty commitMetadataRevision = new SimpleLongProperty(0L);

    private volatile ScheduledFuture<?> autoRefreshFuture;

    public GitManager(Project project, GitClient gitClient, ScheduledExecutorService executorService) {
        this.project = project;
        this.gitClient = gitClient;
        this.executorService = executorService;
    }

    public GitManager(Project project, GitClient gitClient) {
        this(project, gitClient, Executors.newSingleThreadScheduledExecutor());
    }

    public void detectRepository() {
        this.gitClient.detectRepository(this.project.getPath()).ifPresentOrElse(repository -> {
            this.gitRepository.set(repository);
            this.active.set(true);
            startAutoRefresh();
            loadIdentity();
            fetch();
        }, () -> {
            this.gitRepository.set(null);
            this.active.set(false);
            stopAutoRefresh();
        });
    }

    public void refreshStatus() {
        this.executorService.submit(this::refreshStatusInternal);
    }

    public void startAutoRefresh() {
        if (autoRefreshFuture != null && !autoRefreshFuture.isCancelled() && !autoRefreshFuture.isDone())
            return;

        long intervalMillis = getAutoRefreshIntervalMillis();
        autoRefreshFuture = executorService.scheduleAtFixedRate(
            this::refreshStatusInternal,
            0,
            intervalMillis,
            TimeUnit.MILLISECONDS
        );
    }

    public void stopAutoRefresh() {
        if (this.autoRefreshFuture != null) {
            this.autoRefreshFuture.cancel(false);
            this.autoRefreshFuture = null;
        }
    }

    public void setAutoRefreshIntervalMillis(long intervalMillis) {
        if (intervalMillis <= 0)
            throw new IllegalArgumentException("Auto refresh interval must be positive");

        writeAutoRefreshIntervalMillis(intervalMillis);

        if (autoRefreshFuture != null && !autoRefreshFuture.isCancelled() && !autoRefreshFuture.isDone()) {
            stopAutoRefresh();
            startAutoRefresh();
        }
    }

    public ObjectProperty<GitRepoStatus> repoStatusProperty() {
        return repoStatus;
    }

    public BooleanProperty activeProperty() {
        return active;
    }

    public ObjectProperty<GitRepository> gitRepositoryProperty() {
        return gitRepository;
    }

    public GitRepoStatus getRepoStatus() {
        return repoStatus.get();
    }

    public boolean isActive() {
        return active.get();
    }

    public GitRepository getGitRepository() {
        return gitRepository.get();
    }

    public GitSettings getGitSettings() {
        ProjectDataStore dataStore = project.getDataStore();
        return dataStore.readJson(SETTINGS_PATH, GitSettings.class).orElseGet(GitSettings::new);
    }

    public GitSettings getOrCreateGitSettings() {
        ProjectDataStore dataStore = project.getDataStore();
        Optional<GitSettings> settingsOpt = dataStore.readJson(SETTINGS_PATH, GitSettings.class);
        if (settingsOpt.isPresent()) {
            return settingsOpt.get();
        } else {
            var settings = new GitSettings();
            settings.setAutoRefreshIntervalMillis(DEFAULT_AUTO_REFRESH_INTERVAL_MILLIS);
            dataStore.writeJson(SETTINGS_PATH, settings);
            return settings;
        }
    }

    public void saveGitSettings(GitSettings settings) {
        ProjectDataStore dataStore = project.getDataStore();
        dataStore.writeJson(SETTINGS_PATH, settings);
    }

    public void setGitExecutablePath(Path path) {
        this.gitClient.runner.setGitExecutable(path);
    }

    public void commitChanges(GitCommitData commit, boolean pushAfterCommit) {
        this.executorService.submit(() -> {
            gitClient.commitChanges(this.gitRepository.get(), commit, pushAfterCommit);
            refreshStatusInternal();
        });
    }

    public List<GitRemote> getRemotes() {
        GitRepository repository = this.gitRepository.get();
        if (repository != null) {
            return this.gitClient.getRemotes(repository);
        } else {
            return List.of();
        }
    }

    public Optional<GitUpstream> getUpstream() {
        GitRepository repository = this.gitRepository.get();
        if (repository != null) {
            return this.gitClient.getUpstream(repository);
        } else {
            return Optional.empty();
        }
    }

    public void fetch() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.fetch(repository, GitOutputListener.NO_OP, event -> {
                    if (event instanceof GitProgressEvent.Percentage(String phase, int percent)) {
                        Railroad.LOGGER.debug("Git Fetch Progress - {}: {}%", phase, percent);
                    } else if (event instanceof GitProgressEvent.Message(String message)) {
                        Railroad.LOGGER.debug("Git Fetch Message - {}", message);
                    }
                });
                this.lastFetchTimestamp.set(System.currentTimeMillis());
                refreshStatusInternal();
            }
        });
    }

    public LongProperty lastFetchTimestampProperty() {
        return lastFetchTimestamp;
    }

    public long getLastFetchTimestamp() {
        return lastFetchTimestamp.get();
    }

    private void refreshStatusInternal() {
        GitRepository repository = this.gitRepository.get();
        if (repository != null) {
            GitRepoStatus status = this.gitClient.getStatus(repository);
            this.repoStatus.set(status);
//            Railroad.LOGGER.debug("Loaded {} changes from Git repository at {}",
//                status.changes().size(),
//                repository.root());
        } else {
            this.repoStatus.set(null);
        }
    }

    private long getAutoRefreshIntervalMillis() {
        ProjectDataStore dataStore = project.getDataStore();
        Optional<GitSettings> settings = dataStore.readJson(SETTINGS_PATH, GitSettings.class);
        Long interval = settings.map(GitSettings::getAutoRefreshIntervalMillis).orElse(null);
        if (interval == null || interval <= 0) {
            writeAutoRefreshIntervalMillis(DEFAULT_AUTO_REFRESH_INTERVAL_MILLIS);
            return DEFAULT_AUTO_REFRESH_INTERVAL_MILLIS;
        }

        return interval;
    }

    private void writeAutoRefreshIntervalMillis(long intervalMillis) {
        var settings = new GitSettings();
        settings.setAutoRefreshIntervalMillis(intervalMillis);
        project.getDataStore().writeJson(SETTINGS_PATH, settings);
    }

    public void push() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.push(repository, GitOutputListener.NO_OP, event -> {
                    if (event instanceof GitProgressEvent.Percentage(String phase, int percent)) {
                        Railroad.LOGGER.debug("Git Push Progress - {}: {}%", phase, percent);
                    } else if (event instanceof GitProgressEvent.Message(String message)) {
                        Railroad.LOGGER.debug("Git Push Message - {}", message);
                    }
                });
                refreshStatusInternal();
            }
        });
    }

    public void pull() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.pull(repository, GitOutputListener.NO_OP, event -> {
                    if (event instanceof GitProgressEvent.Percentage(String phase, int percent)) {
                        Railroad.LOGGER.debug("Git Pull Progress - {}: {}%", phase, percent);
                    } else if (event instanceof GitProgressEvent.Message(String message)) {
                        Railroad.LOGGER.debug("Git Pull Message - {}", message);
                    }
                });
                refreshStatusInternal();
            }
        });
    }

    public ObjectProperty<GitIdentity> gitIdentityProperty() {
        return gitIdentity;
    }

    public GitIdentity getIdentity() {
        return gitIdentityProperty().get();
    }

    public LongProperty commitMetadataRevisionProperty() {
        return commitMetadataRevision;
    }

    public void loadIdentity() {
        this.executorService.submit(() -> {
            try {
                GitIdentity identity = this.gitClient.getIdentity();
                this.gitIdentity.set(identity);
                Railroad.LOGGER.debug("Loaded Git identity: {}", identity);
            } catch (Exception exception) {
                Railroad.LOGGER.warn("Failed to load Git identity", exception);
            }
        });
    }

    public CompletableFuture<Optional<GitCommitPage>> getRecentCommits(int count) {
        return CompletableFuture.supplyAsync(() -> {
            GitRepository repository = this.gitRepository.get();
            return repository != null
                ? Optional.ofNullable(this.gitClient.getRecentCommits(repository, null, count))
                : Optional.empty();
        }, executorService);
    }

    public CompletableFuture<CommitListMetadata> getCommitListMetadata() {
        return CompletableFuture.supplyAsync(() -> new CommitListMetadata(
            getHeadCommitHash(),
            getTagsByCommit()
        ), executorService);
    }

    public Optional<String> getUnstagedDiff(Path filePath) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || filePath == null)
            return Optional.empty();

        Path repoRoot = repository.root().toAbsolutePath().normalize();
        Path absoluteFile = filePath.toAbsolutePath().normalize();
        if (!absoluteFile.startsWith(repoRoot))
            return Optional.empty();

        Path relativePath = repoRoot.relativize(absoluteFile);
        GitCommand cmd = GitCommands.getUnstagedDiff(repository, relativePath);
        GitResult result = gitClient.runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut() || result.cancelled()) {
            Railroad.LOGGER.warn("git diff was {} for path: {}", result.timedOut() ? "timed out" : "cancelled", filePath);
            return Optional.empty();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git diff failed for path {}: {}", filePath, String.join("\n", result.stderr()));
            return Optional.empty();
        }

        String diffText = result.readAllStdout();
        return Optional.of(diffText);
    }

    public String getHeadCommitHash() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        GitCommand cmd = GitCommands.getHeadCommitHash(repository);
        GitResult result = gitClient.runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut() || result.cancelled()) {
            Railroad.LOGGER.warn("git rev-parse was {} for repository at: {}",
                result.timedOut() ? "timed out" : "cancelled",
                repository.root());
            return null;
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git rev-parse failed for repository at {}: {}",
                repository.root(),
                String.join("\n", result.stderr()));
            return null;
        }

        String commitHash = result.readAllStdout().trim();
        return commitHash.isEmpty() ? null : commitHash;
    }

    public List<String> getTagsPointingToCommit(String hash) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        GitCommand cmd = GitCommands.getTagsPointingToCommit(repository, hash);
        GitResult result = gitClient.runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut() || result.cancelled()) {
            Railroad.LOGGER.warn("git tag was {} for repository at: {}",
                result.timedOut() ? "timed out" : "cancelled",
                repository.root());
            return List.of();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git tag failed for repository at {}: {}",
                repository.root(),
                String.join("\n", result.stderr()));
            return List.of();
        }

        String stdout = result.readAllStdout().trim();
        if (stdout.isEmpty()) {
            return List.of();
        } else {
            String[] tags = stdout.split("\n");
            return List.of(tags);
        }
    }

    public Map<String, List<String>> getTagsByCommit() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return Map.of();

        GitCommand cmd = GitCommands.getAllTagsWithCommits(repository);
        GitResult result = gitClient.runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut() || result.cancelled()) {
            Railroad.LOGGER.warn("git show-ref was {} for repository at: {}",
                result.timedOut() ? "timed out" : "cancelled",
                repository.root());
            return Map.of();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git show-ref failed for repository at {}: {}",
                repository.root(),
                String.join("\n", result.stderr()));
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

    public List<String> getAllBranches() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        GitCommand cmd = GitCommands.getAllBranches(repository);
        GitResult result = gitClient.runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut() || result.cancelled()) {
            Railroad.LOGGER.warn("git branch was {} for repository at: {}",
                result.timedOut() ? "timed out" : "cancelled",
                repository.root());
            return List.of();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git branch failed for repository at {}: {}",
                repository.root(),
                String.join("\n", result.stderr()));
            return List.of();
        }

        String stdout = result.readAllStdout().trim();
        if (stdout.isEmpty()) {
            return List.of();
        } else {
            String[] branches = stdout.split("\n");
            return List.of(branches);
        }
    }

    public List<GitAuthor> getAllAuthors(boolean includeEmail) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        GitCommand cmd = GitCommands.getAllAuthors(repository, includeEmail);
        GitResult result = gitClient.runner.run(cmd, null, null, GitResultCaptureMode.TEXT_LINES);

        if (result.timedOut() || result.cancelled()) {
            Railroad.LOGGER.warn("git shortlog was {} for repository at: {}",
                result.timedOut() ? "timed out" : "cancelled",
                repository.root());
            return List.of();
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git shortlog failed for repository at {}: {}",
                repository.root(),
                String.join("\n", result.stderr()));
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

    public void getAllCommits(Consumer<List<GitCommit>> onPage, Runnable onDone, int pageSize) {
        executorService.submit(() -> {
            try {
                GitRepository repository = this.gitRepository.get();
                if (repository == null)
                    return;

                boolean morePages = true;
                String lastCommitHash = null;
                while (morePages) {
                    GitCommitPage page = this.gitClient.getRecentCommits(repository, lastCommitHash, pageSize);
                    if (page != null && !page.commits().isEmpty()) {
                        if (onPage != null) {
                            onPage.accept(page.commits());
                        }
                        lastCommitHash = page.commits().getLast().hash();
                        morePages = page.nextCursor() != null;
                    } else {
                        morePages = false;
                    }
                }
            } finally {
                if (onDone != null) {
                    onDone.run();
                }
            }
        });
    }

    public long getRepositoryCreationDate() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return 0L;

        GitCommand cmd = GitCommands.getRepositoryCreationDate(repository);
        GitResult result = gitClient.runner.run(cmd, null, null, GitResultCaptureMode.TEXT_WHOLE);

        if (result.timedOut() || result.cancelled()) {
            Railroad.LOGGER.warn("git rev-list was {} for repository at: {}",
                result.timedOut() ? "timed out" : "cancelled",
                repository.root());
            return 0L;
        }

        if (result.exitCode() != 0) {
            Railroad.LOGGER.warn("git rev-list failed for repository at {}: {}",
                repository.root(),
                String.join("\n", result.stderr()));
            return 0L;
        }

        String stdout = result.readAllStdout().trim();
        try {
            return Long.parseLong(stdout);
        } catch (NumberFormatException exception) {
            Railroad.LOGGER.warn("Failed to parse repository creation date '{}' for repository at {}",
                stdout,
                repository.root());
            return 0L;
        }
    }

    public List<GitAdditionsDeletions> getAdditionsDeletions(String commitHash) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getAdditionsDeletions(repository, commitHash);
    }

    public GitCommit getCommitWithBody(GitCommit commit) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || commit == null)
            return commit;

        if (commit.body() != null && !commit.body().isEmpty())
            return commit;

        String message = this.gitClient.getCommitMessage(repository, commit.hash());
        message = message.substring(message.indexOf('\n') + 1).strip(); // Remove the first line (summary)
        return GitCommit.withBody(commit, message);
    }

    public void stashChanges(String message, boolean includeUntracked) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.stashChanges(repository, message, includeUntracked);
                refreshStatusInternal();
            }
        });
    }

    public void stashPop() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.stashPop(repository);
                refreshStatusInternal();
            }
        });
    }

    public void checkoutCommit(String hash) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.checkoutCommit(repository, hash, getIdentity().gitVersion());
                refreshStatusInternal();
            }
        });
    }

    public void resetHard() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.resetHard(repository);
                refreshStatusInternal();
            }
        });
    }

    public void cleanUntrackedFiles() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.cleanUntrackedFiles(repository);
                refreshStatusInternal();
            }
        });
    }

    public Optional<GitCommit> getCurrentCommit() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return Optional.empty();

        return Optional.ofNullable(this.gitClient.getCurrentCommit(repository));
    }

    public boolean isValidBranchName(String string) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return false;

        return this.gitClient.isValidBranchName(repository, string);
    }

    public void createBranch(String branchName, String hash, boolean checkoutAfter) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.createBranch(repository, branchName, hash);
                if (checkoutAfter) {
                    checkoutBranch(branchName);
                } else {
                    refreshStatusInternal();
                }
            }
        });
    }

    public void checkoutBranch(String branchName) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.checkoutBranch(repository, branchName, getIdentity().gitVersion());
                refreshStatusInternal();
            }
        });
    }

    public boolean doesTagExist(String tagName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return false;

        return this.gitClient.doesTagExist(repository, tagName);
    }

    public boolean isValidTagName(String tagName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return false;

        return this.gitClient.isValidTagName(repository, tagName);
    }

    public void createTag(String tagName, String hash, @Nullable String message, boolean overwrite) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.createTag(repository, tagName, hash, message, overwrite);
                this.commitMetadataRevision.set(this.commitMetadataRevision.get() + 1L);
                refreshStatusInternal();
            }
        });
    }

    public boolean isInCherryPickState() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return false;

        return this.gitClient.isInCherryPickState(repository);
    }

    public CompletableFuture<CherryPickResult> cherryPickCommit(String commitHash) {
        return CompletableFuture.supplyAsync(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository == null)
                return CherryPickResult.FAILED;

            return this.gitClient.cherryPickCommit(repository, commitHash);
        }, executorService);
    }

    public void continueCherryPick() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.continueCherryPick(repository);
                refreshStatusInternal();
            }
        });
    }

    public void abortCherryPick() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.abortCherryPick(repository);
                refreshStatusInternal();
            }
        });
    }

    public void quitCherryPick() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.quitCherryPick(repository);
                refreshStatusInternal();
            }
        });
    }

    public void revertCommit(String commitHash) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.revertCommit(repository, commitHash);
                refreshStatusInternal();
            }
        });
    }

    public boolean hasUncommittedChanges() {
        GitRepoStatus status = this.repoStatus.get();
        return status != null && !status.changes().isEmpty();
    }

    public String getCurrentBranch() {
        return Optional.ofNullable(getRepoStatus())
            .map(GitRepoStatus::branch)
            .orElse(null);
    }
}
