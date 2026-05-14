package dev.railroadide.railroad.vcs.git;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.project.data.ProjectDataStore;
import dev.railroadide.railroad.vcs.git.branch.GitBranch;
import dev.railroadide.railroad.vcs.git.branch.GitBranchLastCommit;
import dev.railroadide.railroad.vcs.git.branch.GitBranchStatus;
import dev.railroadide.railroad.vcs.git.commit.CommitListMetadata;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import dev.railroadide.railroad.vcs.git.commit.GitCommitData;
import dev.railroadide.railroad.vcs.git.commit.GitCommitPage;
import dev.railroadide.railroad.vcs.git.diff.GitAdditionsDeletions;
import dev.railroadide.railroad.vcs.git.execution.GitOutputListener;
import dev.railroadide.railroad.vcs.git.execution.progress.GitProgressEvent;
import dev.railroadide.railroad.vcs.git.identity.GitAuthor;
import dev.railroadide.railroad.vcs.git.identity.GitIdentity;
import dev.railroadide.railroad.vcs.git.remote.GitRemote;
import dev.railroadide.railroad.vcs.git.remote.GitUpstream;
import dev.railroadide.railroad.vcs.git.stash.GitStashEntry;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import dev.railroadide.railroad.vcs.git.status.GitRepoStatus;
import dev.railroadide.railroad.vcs.git.util.*;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Coordinates repository state, settings, and asynchronous git actions for a project.
 */
public class GitManager {
    private static final String SETTINGS_PATH = "vcs/git.json";
    private static final long DEFAULT_AUTO_REFRESH_INTERVAL_MILLIS = 5000L;

    private final Project project;
    private final GitClient gitClient;

    private final ScheduledExecutorService executorService;

    private final ObjectProperty<GitRepoStatus> repoStatus = new SimpleObjectProperty<>();
    private final BooleanProperty active = new SimpleBooleanProperty(false);
    private final ObjectProperty<GitRepository> gitRepository = new SimpleObjectProperty<>();
    private final ObjectProperty<GitIdentity> gitIdentity = new SimpleObjectProperty<>();
    private final LongProperty commitMetadataRevision = new SimpleLongProperty(0L);
    private final MapProperty<String, Long> remoteFetchTimestamps = new SimpleMapProperty<>(FXCollections.observableHashMap());

    private volatile ScheduledFuture<?> autoRefreshFuture;

    /**
     * Creates a manager with externally provided executor.
     *
     * @param project owning project
     * @param gitClient git client backend
     * @param executorService executor used for async operations
     */
    public GitManager(Project project, GitClient gitClient, ScheduledExecutorService executorService) {
        this.project = project;
        this.gitClient = gitClient;
        this.executorService = executorService;
    }

    /**
     * Creates a manager with a single-threaded default executor.
     *
     * @param project owning project
     * @param gitClient git client backend
     */
    public GitManager(Project project, GitClient gitClient) {
        this(project, gitClient, Executors.newSingleThreadScheduledExecutor());
    }

    /**
     * Detects repository for the current project path and updates manager state.
     */
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

    /**
     * Schedules an asynchronous status refresh.
     */
    public void refreshStatus() {
        this.executorService.submit(this::refreshStatusInternal);
    }

    /**
     * Starts periodic automatic status refresh.
     */
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

    /**
     * Stops periodic automatic status refresh.
     */
    public void stopAutoRefresh() {
        if (this.autoRefreshFuture != null) {
            this.autoRefreshFuture.cancel(false);
            this.autoRefreshFuture = null;
        }
    }

    /**
     * Updates auto-refresh interval and restarts scheduler if needed.
     *
     * @param intervalMillis refresh interval in milliseconds
     * @throws IllegalArgumentException when interval is non-positive
     */
    public void setAutoRefreshIntervalMillis(long intervalMillis) {
        if (intervalMillis <= 0)
            throw new IllegalArgumentException("Auto refresh interval must be positive");

        writeAutoRefreshIntervalMillis(intervalMillis);

        if (autoRefreshFuture != null && !autoRefreshFuture.isCancelled() && !autoRefreshFuture.isDone()) {
            stopAutoRefresh();
            startAutoRefresh();
        }
    }

    /**
     * Exposes repository status property.
     *
     * @return observable status property
     */
    public ObjectProperty<GitRepoStatus> repoStatusProperty() {
        return repoStatus;
    }

    /**
     * Exposes active state property.
     *
     * @return observable active property
     */
    public BooleanProperty activeProperty() {
        return active;
    }

    /**
     * Exposes detected repository property.
     *
     * @return observable repository property
     */
    public ObjectProperty<GitRepository> gitRepositoryProperty() {
        return gitRepository;
    }

    /**
     * Gets current repository status snapshot.
     *
     * @return current status, or {@code null}
     */
    public GitRepoStatus getRepoStatus() {
        return repoStatus.get();
    }

    /**
     * Indicates whether git integration is active.
     *
     * @return {@code true} when a repository is active
     */
    public boolean isActive() {
        return active.get();
    }

    /**
     * Gets currently detected repository.
     *
     * @return repository, or {@code null}
     */
    public GitRepository getGitRepository() {
        return gitRepository.get();
    }

    /**
     * Loads persisted git settings.
     *
     * @return loaded settings, or defaults when absent
     */
    public GitSettings getGitSettings() {
        ProjectDataStore dataStore = project.getDataStore();
        return dataStore.readJson(SETTINGS_PATH, GitSettings.class).orElseGet(GitSettings::new);
    }

    /**
     * Loads settings, creating and persisting defaults when missing.
     *
     * @return existing or newly created settings
     */
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

    /**
     * Persists git settings.
     *
     * @param settings settings to save
     */
    public void saveGitSettings(GitSettings settings) {
        ProjectDataStore dataStore = project.getDataStore();
        dataStore.writeJson(SETTINGS_PATH, settings);
    }

    /**
     * Sets git executable path used by the client.
     *
     * @param path git executable path
     */
    public void setGitExecutablePath(Path path) {
        this.gitClient.setGitExecutable(path);
    }

    /**
     * Submits commit operation and refreshes status afterward.
     *
     * @param commit commit data
     * @param pushAfterCommit whether to push after commit
     */
    public void commitChanges(GitCommitData commit, boolean pushAfterCommit) {
        this.executorService.submit(() -> {
            gitClient.commitChanges(this.gitRepository.get(), commit, pushAfterCommit);
            refreshStatusInternal();
        });
    }

    /**
     * Gets remotes for the active repository.
     *
     * @return remotes, or empty list when no repository is active
     */
    public List<GitRemote> getRemotes() {
        GitRepository repository = this.gitRepository.get();
        if (repository != null) {
            return this.gitClient.getRemotes(repository);
        } else {
            return List.of();
        }
    }

    /**
     * Gets upstream for the active repository.
     *
     * @return upstream, or empty when unavailable
     */
    public Optional<GitUpstream> getUpstream() {
        GitRepository repository = this.gitRepository.get();
        if (repository != null) {
            return this.gitClient.getUpstream(repository);
        } else {
            return Optional.empty();
        }
    }

    /**
     * Schedules fetch for active repository and refreshes status.
     */
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
                this.remoteFetchTimestamps.put(getUpstream().map(GitUpstream::remoteName).orElse(""), System.currentTimeMillis());
                refreshStatusInternal();
            }
        });
    }

    /**
     * Exposes latest fetch timestamp across remotes.
     *
     * @return observable latest fetch timestamp
     */
    public ObservableValue<Long> lastFetchTimestampProperty() {
        return remoteFetchTimestamps.map(map -> map.values().stream().max(Long::compareTo).orElse(0L));
    }

    /**
     * Gets latest fetch timestamp across remotes.
     *
     * @return epoch-millis timestamp, or {@code 0}
     */
    public long getLastFetchTimestamp() {
        return lastFetchTimestampProperty().getValue();
    }

    /**
     * Gets latest fetch timestamp for a specific remote.
     *
     * @param remote remote descriptor
     * @return epoch-millis timestamp, or {@code 0}
     */
    public long getLastFetchTimestamp(GitRemote remote) {
        return this.remoteFetchTimestamps.getOrDefault(remote.name(), 0L);
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
        ProjectDataStore dataStore = project.getDataStore();
        GitSettings settings = dataStore.readJson(SETTINGS_PATH, GitSettings.class)
            .orElseGet(GitSettings::new);
        settings.setAutoRefreshIntervalMillis(intervalMillis);
        dataStore.writeJson(SETTINGS_PATH, settings);
    }

    /**
     * Schedules asynchronous push for the active repository.
     */
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

    /**
     * Schedules asynchronous pull for the active repository.
     */
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

    /**
     * Exposes git identity property.
     *
     * @return observable identity property
     */
    public ObjectProperty<GitIdentity> gitIdentityProperty() {
        return gitIdentity;
    }

    /**
     * Gets current identity snapshot.
     *
     * @return identity value, or {@code null}
     */
    public GitIdentity getIdentity() {
        return gitIdentityProperty().get();
    }

    /**
     * Exposes revision counter for commit metadata refreshes.
     *
     * @return observable revision property
     */
    public LongProperty commitMetadataRevisionProperty() {
        return commitMetadataRevision;
    }

    /**
     * Loads identity asynchronously and updates property.
     */
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

    /**
     * Loads recent commits asynchronously.
     *
     * @param count max number of commits
     * @return future containing optional commit page
     */
    public CompletableFuture<Optional<GitCommitPage>> getRecentCommits(int count) {
        return CompletableFuture.supplyAsync(() -> {
            GitRepository repository = this.gitRepository.get();
            return repository != null
                ? Optional.ofNullable(this.gitClient.getRecentCommits(repository, null, count))
                : Optional.empty();
        }, executorService);
    }

    /**
     * Loads metadata used by commit list views.
     *
     * @return future containing commit list metadata
     */
    public CompletableFuture<CommitListMetadata> getCommitListMetadata() {
        return CompletableFuture.supplyAsync(() -> new CommitListMetadata(
            getHeadCommitHash(),
            getTagsByCommit()
        ), executorService);
    }

    /**
     * Gets unstaged diff text for a file under the repository root.
     *
     * @param filePath absolute or relative file path
     * @return diff text when available
     */
    public Optional<String> getUnstagedDiff(Path filePath) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || filePath == null)
            return Optional.empty();

        Path repoRoot = repository.root().toAbsolutePath().normalize();
        Path absoluteFile = filePath.toAbsolutePath().normalize();
        if (!absoluteFile.startsWith(repoRoot))
            return Optional.empty();

        Path relativePath = repoRoot.relativize(absoluteFile);
        return this.gitClient.getUnstagedDiffText(repository, relativePath);
    }

    /**
     * Gets HEAD commit hash for the active repository.
     *
     * @return HEAD hash, or {@code null} when unavailable
     */
    public String getHeadCommitHash() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        return this.gitClient.getHeadCommitHash(repository);
    }

    /**
     * Gets tags that point to the given commit.
     *
     * @param hash commit hash
     * @return tag names
     */
    public List<String> getTagsPointingToCommit(String hash) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getTagsPointingToCommit(repository, hash);
    }

    /**
     * Gets map of commit hashes to their tags.
     *
     * @return tags by commit hash
     */
    public Map<String, List<String>> getTagsByCommit() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return Map.of();

        return this.gitClient.getTagsByCommit(repository);
    }

    /**
     * Gets all branch names.
     *
     * @return local and remote branch names
     */
    public List<String> getAllBranchNames() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getAllBranches(repository);
    }

    /**
     * Gets all local branch names.
     *
     * @return local branch names
     */
    public List<String> getAllLocalBranchNames() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getAllLocalBranches(repository);
    }

    /**
     * Gets all remote branch names.
     *
     * @return remote branch names
     */
    public List<String> getAllRemoteBranchNames() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getAllRemoteBranches(repository);
    }

    /**
     * Gets all repository authors.
     *
     * @param includeEmail whether emails should be included
     * @return parsed author entries
     */
    public List<GitAuthor> getAllAuthors(boolean includeEmail) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getAllAuthors(repository, includeEmail);
    }

    /**
     * Streams all commits in pages to callbacks.
     *
     * @param onPage callback invoked for each page
     * @param onDone callback invoked when iteration finishes
     * @param pageSize page size for each request
     */
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

    /**
     * Gets repository creation timestamp.
     *
     * @return epoch-second timestamp, or {@code 0}
     */
    public long getRepositoryCreationDate() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return 0L;

        return this.gitClient.getRepositoryCreationDate(repository);
    }

    /**
     * Gets additions/deletions stats for a commit.
     *
     * @param commitHash commit hash
     * @return per-file additions/deletions list
     */
    public List<GitAdditionsDeletions> getAdditionsDeletions(String commitHash) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getAdditionsDeletions(repository, commitHash);
    }

    /**
     * Ensures a commit object contains message body content.
     *
     * @param commit source commit
     * @return commit with body when available
     */
    public GitCommit getCommitWithBody(GitCommit commit) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || commit == null)
            return commit;

        if (commit.body() != null && !commit.body().isEmpty())
            return commit;

        String message = this.gitClient.getCommitMessage(repository, commit.hash());
        if (message == null || message.isEmpty())
            return commit;

        int newlineIndex = message.indexOf('\n');
        String body;
        if (newlineIndex >= 0 && newlineIndex + 1 < message.length()) {
            body = message.substring(newlineIndex + 1).strip(); // Remove the first line (summary)
        } else {
            body = "";
        }

        return GitCommit.withBody(commit, body);
    }

    /**
     * Schedules stash creation for the active repository.
     *
     * @param message stash message
     * @param includeUntracked whether untracked files should be included
     */
    public void stashChanges(String message, boolean includeUntracked) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.stashChanges(repository, message, includeUntracked);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules pop of latest stash.
     */
    public void stashPop() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.stashPop(repository);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules pop of a specific stash.
     *
     * @param stashRef stash reference
     */
    public void stashPop(String stashRef) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.stashPop(repository, stashRef);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Gets stash entries for active repository.
     *
     * @return stash entries
     */
    public List<GitStashEntry> getStashes() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getStashes(repository);
    }

    /**
     * Schedules apply-and-drop for a stash entry.
     *
     * @param stashRef stash reference
     */
    public void stashApply(String stashRef) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.stashApply(repository, stashRef);
                this.gitClient.stashDrop(repository, stashRef);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules drop of a stash entry.
     *
     * @param stashRef stash reference
     */
    public void stashDrop(String stashRef) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.stashDrop(repository, stashRef);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Gets file changes for a stash.
     *
     * @param stashRef stash reference
     * @return file changes, or empty list when unavailable
     */
    public List<GitFileChange> getStashChanges(String stashRef) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || stashRef == null || stashRef.isBlank())
            return List.of();

        return this.gitClient.getStashChanges(repository, stashRef);
    }

    /**
     * Gets stash diff text for a file.
     *
     * @param stashRef stash reference
     * @param filePath file path inside repository
     * @return diff text when available
     */
    public Optional<String> getStashDiff(String stashRef, Path filePath) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || stashRef == null || stashRef.isBlank() || filePath == null)
            return Optional.empty();

        Path repoRoot = repository.root().toAbsolutePath().normalize();
        Path absoluteFile = filePath.toAbsolutePath().normalize();
        if (!absoluteFile.startsWith(repoRoot))
            return Optional.empty();

        Path relativePath = repoRoot.relativize(absoluteFile);
        return this.gitClient.getStashDiffText(repository, stashRef, relativePath);
    }

    /**
     * Schedules detached checkout of a commit.
     *
     * @param hash commit hash
     */
    public void checkoutCommit(String hash) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.checkoutCommit(repository, hash, getIdentity().gitVersion());
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules hard reset.
     */
    public void resetHard() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.resetHard(repository);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules cleanup of untracked files.
     */
    public void cleanUntrackedFiles() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.cleanUntrackedFiles(repository);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Gets current commit for active repository.
     *
     * @return optional current commit
     */
    public Optional<GitCommit> getCurrentCommit() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return Optional.empty();

        return Optional.ofNullable(this.gitClient.getCurrentCommit(repository));
    }

    /**
     * Validates a branch name in the active repository.
     *
     * @param string branch name candidate
     * @return {@code true} when valid
     */
    public boolean isValidBranchName(String string) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return false;

        return this.gitClient.isValidBranchName(repository, string);
    }

    /**
     * Schedules branch creation with optional checkout.
     *
     * @param branchName branch name
     * @param hash start-point hash
     * @param checkoutAfter whether to checkout after creation
     */
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

    /**
     * Schedules branch checkout.
     *
     * @param branchName branch name
     */
    public void checkoutBranch(String branchName) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.checkoutBranch(repository, branchName, getIdentity().gitVersion());
                refreshStatusInternal();
            }
        });
    }

    /**
     * Checks if a tag exists.
     *
     * @param tagName tag name
     * @return {@code true} when tag exists
     */
    public boolean doesTagExist(String tagName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return false;

        return this.gitClient.doesTagExist(repository, tagName);
    }

    /**
     * Validates a tag name.
     *
     * @param tagName tag name candidate
     * @return {@code true} when valid
     */
    public boolean isValidTagName(String tagName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return false;

        return this.gitClient.isValidTagName(repository, tagName);
    }

    /**
     * Schedules tag creation/update.
     *
     * @param tagName tag name
     * @param hash target hash
     * @param message optional message
     * @param overwrite whether overwrite is allowed
     */
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

    /**
     * Checks whether active repository is in cherry-pick state.
     *
     * @return {@code true} when cherry-pick metadata exists
     */
    public boolean isInCherryPickState() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return false;

        return this.gitClient.isInCherryPickState(repository);
    }

    /**
     * Schedules asynchronous cherry-pick.
     *
     * @param commitHash commit hash to cherry-pick
     * @return future with cherry-pick result
     */
    public CompletableFuture<CherryPickResult> cherryPickCommit(String commitHash) {
        return CompletableFuture.supplyAsync(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository == null)
                return CherryPickResult.FAILED;

            return this.gitClient.cherryPickCommit(repository, commitHash);
        }, executorService);
    }

    /**
     * Schedules continue for cherry-pick.
     */
    public void continueCherryPick() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.continueCherryPick(repository);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules abort for cherry-pick.
     */
    public void abortCherryPick() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.abortCherryPick(repository);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules quit for cherry-pick state.
     */
    public void quitCherryPick() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.quitCherryPick(repository);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules commit revert.
     *
     * @param commitHash commit hash to revert
     */
    public void revertCommit(String commitHash) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.revertCommit(repository, commitHash);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Checks whether working tree has changes based on cached status.
     *
     * @return {@code true} when cached status contains changes
     */
    public boolean hasUncommittedChanges() {
        GitRepoStatus status = this.repoStatus.get();
        return status != null && !status.changes().isEmpty();
    }

    /**
     * Checks whether a specific branch has uncommitted changes.
     *
     * @param branchName branch name
     * @return {@code true} when branch has uncommitted changes
     */
    public boolean hasUncommittedChanges(String branchName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return false;

        return this.gitClient.hasUncommittedChanges(repository, branchName);
    }

    /**
     * Gets current branch name from cached status.
     *
     * @return current branch name, or {@code null}
     */
    public String getCurrentBranch() {
        return Optional.ofNullable(getRepoStatus())
            .map(GitRepoStatus::branch)
            .orElse(null);
    }

    /**
     * Builds local branch descriptors with sync metadata.
     *
     * @return local branch descriptors
     */
    public List<GitBranch.LocalGitBranch> getAllLocalBranches() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        List<String> localBranchNames = getAllLocalBranchNames();
        List<GitBranch.LocalGitBranch> localBranches = new ArrayList<>();
        for (String branchName : localBranchNames) {
            @Nullable String remoteName = getRemoteTrackingBranch(branchName);
            boolean isCurrent = branchName.equals(getCurrentBranch());
            int[] aheadBehind = remoteName != null ? getAheadBehindCounts(branchName, remoteName) : new int[]{0, 0};
            int aheadCount = aheadBehind[0];
            int behindCount = aheadBehind[1];
            String lastCommitHash = getLastCommitHash(branchName);
            Long lastCommitTimestampEpochSeconds = getLastCommitTimestamp(branchName);
            String lastCommitMessage = getCommitMessage(lastCommitHash);
            GitAuthor lastCommitAuthor = getCommitAuthor(lastCommitHash);
            var lastCommit = new GitBranchLastCommit(
                lastCommitHash,
                lastCommitTimestampEpochSeconds,
                lastCommitMessage,
                lastCommitAuthor
            );

            GitBranchStatus status = determineBranchStatus(branchName, true);
            localBranches.add(new GitBranch.LocalGitBranch(
                branchName,
                remoteName,
                isCurrent,
                aheadCount,
                behindCount,
                lastCommit,
                status
            ));
        }

        return localBranches;
    }

    /**
     * Builds remote branch descriptors.
     *
     * @return remote branch descriptors
     */
    public List<GitBranch.RemoteGitBranch> getAllRemoteBranches() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        List<String> remoteBranchNames = getAllRemoteBranchNames();
        List<GitBranch.RemoteGitBranch> remoteBranches = new ArrayList<>();
        for (String branchName : remoteBranchNames) {
            if (branchName.endsWith("/HEAD"))
                continue;

            int slashIndex = branchName.indexOf('/');
            String remoteName = slashIndex > 0 ? branchName.substring(0, slashIndex) : "remote";
            String lastCommitHash = getLastCommitHash(branchName);
            String lastCommitMessage = getCommitMessage(lastCommitHash);
            GitAuthor lastCommitAuthor = getCommitAuthor(lastCommitHash);
            var lastCommit = new GitBranchLastCommit(
                lastCommitHash,
                null,
                lastCommitMessage,
                lastCommitAuthor
            );
            GitBranchStatus status = determineBranchStatus(branchName, false);
            remoteBranches.add(new GitBranch.RemoteGitBranch(
                branchName,
                remoteName,
                lastCommit,
                status
            ));
        }

        return remoteBranches;
    }

    /**
     * Determines display status for a branch.
     *
     * @param branchName branch name
     * @param local whether branch is local
     * @return branch status
     */
    public GitBranchStatus determineBranchStatus(String branchName, boolean local) {
        boolean hasUncommittedChanges = hasUncommittedChanges(branchName);
        if (local) {
            if (hasUncommittedChanges) {
                return GitBranchStatus.DIRTY;
            } else {
                String remoteName = getRemoteTrackingBranch(branchName);
                if (remoteName != null) {
                    int[] aheadBehind = getAheadBehindCounts(branchName, remoteName);
                    int aheadCount = aheadBehind[0];
                    int behindCount = aheadBehind[1];
                    if (aheadCount > 0 && behindCount > 0) {
                        return GitBranchStatus.DIRTY;
                    } else if (aheadCount > 0) {
                        return GitBranchStatus.LOCAL;
                    } else if (behindCount > 0) {
                        return GitBranchStatus.REMOTE;
                    }
                }
                return GitBranchStatus.CLEAN;
            }
        } else {
            return hasUncommittedChanges ? GitBranchStatus.DIRTY : GitBranchStatus.CLEAN;
        }
    }

    /**
     * Gets upstream tracking branch for a local branch.
     *
     * @param localBranchName local branch name
     * @return upstream branch reference, or {@code null}
     */
    public String getRemoteTrackingBranch(String localBranchName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        return this.gitClient.getRemoteTrackingBranch(repository, localBranchName);
    }

    /**
     * Gets ahead/behind counts for two branches.
     *
     * @param localBranchName local branch
     * @param remoteBranchName remote branch
     * @return two-element array: `[ahead, behind]`
     */
    public int[] getAheadBehindCounts(String localBranchName, String remoteBranchName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return new int[]{0, 0};

        return this.gitClient.getAheadBehindCounts(repository, localBranchName, remoteBranchName);
    }

    /**
     * Gets latest commit hash for a branch.
     *
     * @param branchName branch name
     * @return commit hash, or {@code null}
     */
    public String getLastCommitHash(String branchName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        return this.gitClient.getLastCommitHash(repository, branchName);
    }

    /**
     * Gets latest commit timestamp for a branch.
     *
     * @param branchName branch name
     * @return epoch-second timestamp, or {@code null}
     */
    public Long getLastCommitTimestamp(String branchName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        return this.gitClient.getLastCommitTimestamp(repository, branchName);
    }

    /**
     * Gets first-line commit message for a hash.
     *
     * @param hash commit hash
     * @return commit subject, or {@code null}
     */
    public String getCommitMessage(String hash) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || hash == null || hash.isBlank())
            return null;

        return this.gitClient.getCommitMessage(repository, hash).lines().findFirst().orElse(null);
    }

    /**
     * Gets commit author for a hash.
     *
     * @param hash commit hash
     * @return author info, or {@code null}
     */
    public GitAuthor getCommitAuthor(String hash) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || hash == null || hash.isBlank())
            return null;

        return this.gitClient.getCommitAuthor(repository, hash);
    }

    /**
     * Schedules upstream assignment for current local branch.
     *
     * @param localBranchName local branch name
     * @param remoteBranchName remote branch name
     */
    public void setBranchUpstream(String localBranchName, String remoteBranchName) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.setBranchUpstream(repository, localBranchName, remoteBranchName);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules upstream removal for a local branch.
     *
     * @param localBranchName local branch name
     */
    public void unsetBranchUpstream(String localBranchName) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.unsetBranchUpstream(repository, localBranchName);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules branch deletion.
     *
     * @param branchName branch name
     * @param force whether delete should be forced
     */
    public void deleteBranch(String branchName, boolean force) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.deleteBranch(repository, branchName, force);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules branch rename.
     *
     * @param oldName current branch name
     * @param newName new branch name
     * @param force whether rename should be forced
     */
    public void renameBranch(String oldName, String newName, boolean force) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.renameBranch(repository, oldName, newName, force);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Gets configured URLs for a remote.
     *
     * @param remote remote descriptor
     * @return remote URLs
     */
    public List<String> getRemoteUrls(GitRemote remote) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || remote == null)
            return List.of();

        return this.gitClient.getRemoteUrls(repository, remote);
    }

    /**
     * Checks whether prune is enabled for a remote.
     *
     * @param remote remote descriptor
     * @return {@code true} when prune is enabled
     */
    public boolean isPruningEnabled(GitRemote remote) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || remote == null)
            return false;

        return this.gitClient.isPruningEnabled(repository, remote);
    }

    /**
     * Schedules fetch-all-remotes operation.
     */
    public void fetchAllRemotes() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.fetchAllRemotes(repository, GitOutputListener.NO_OP, event -> {
                    if (event instanceof GitProgressEvent.Percentage(String phase, int percent)) {
                        Railroad.LOGGER.debug("Git Fetch All Remotes Progress - {}: {}%", phase, percent);
                    } else if (event instanceof GitProgressEvent.Message(String message)) {
                        Railroad.LOGGER.debug("Git Fetch All Remotes Message - {}", message);
                    }
                });
                refreshStatusInternal();
                for (GitRemote remote : getRemotes()) {
                    this.remoteFetchTimestamps.put(remote.name(), System.currentTimeMillis());
                }
            }
        });
    }

    /**
     * Schedules prune-all-remotes operation.
     */
    public void pruneAllRemotes() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.pruneAllRemotes(repository, GitOutputListener.NO_OP, event -> {
                    if (event instanceof GitProgressEvent.Percentage(String phase, int percent)) {
                        Railroad.LOGGER.debug("Git Prune All Remotes Progress - {}: {}%", phase, percent);
                    } else if (event instanceof GitProgressEvent.Message(String message)) {
                        Railroad.LOGGER.debug("Git Prune All Remotes Message - {}", message);
                    }
                });
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules garbage collection operation.
     */
    public void gc() {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.gc(repository, GitOutputListener.NO_OP, event -> {
                    if (event instanceof GitProgressEvent.Percentage(String phase, int percent)) {
                        Railroad.LOGGER.debug("Git Prune Progress - {}: {}%", phase, percent);
                    } else if (event instanceof GitProgressEvent.Message(String message)) {
                        Railroad.LOGGER.debug("Git Prune Message - {}", message);
                    }
                });
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules add-remote operation.
     *
     * @param name remote name
     * @param fetchUrl fetch URL
     * @param pushUrl push URL
     */
    public void addRemote(String name, String fetchUrl, String pushUrl) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.addRemote(repository, name, fetchUrl, pushUrl);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules update-remote operation.
     *
     * @param oldName existing remote name
     * @param newName new remote name
     * @param fetchUrl fetch URL
     * @param pushUrl push URL
     */
    public void updateRemote(String oldName, String newName, String fetchUrl, String pushUrl) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.updateRemote(repository, oldName, newName, fetchUrl, pushUrl);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules remove-remote operation.
     *
     * @param name remote name
     */
    public void removeRemote(String name) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.removeRemote(repository, name);
                refreshStatusInternal();
            }
        });
    }

    /**
     * Gets effective pull strategy for active repository.
     *
     * @return pull strategy, or {@code null} when no repository is active
     */
    public GitPullStrategy getPullStrategy() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        return this.gitClient.getPullStrategy(repository, getCurrentBranch());
    }

    /**
     * Gets configured push strategy for active repository.
     *
     * @return push strategy, or {@code null} when no repository is active
     */
    public GitPushStrategy getPushStrategy() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        return this.gitClient.getPushStrategy(repository);
    }

    /**
     * Schedules push-strategy update.
     *
     * @param strategy push strategy
     */
    public void setPushStrategy(GitPushStrategy strategy) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.setPushStrategy(repository, strategy, getCurrentBranch());
                refreshStatusInternal();
            }
        });
    }

    /**
     * Schedules pull-strategy update.
     *
     * @param strategy pull strategy
     */
    public void setPullStrategy(GitPullStrategy strategy) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                String currentBranch = getCurrentBranch();
                if (currentBranch != null) {
                    this.gitClient.setPullStrategy(repository, strategy);
                    refreshStatusInternal();
                }
            }
        });
    }

    /**
     * Gets current remote inferred from upstream or fallback rules.
     *
     * @return current remote, or {@code null}
     */
    public @Nullable GitRemote getCurrentRemote() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        String currentBranch = getCurrentBranch();
        List<GitRemote> remotes = getRemotes();
        if (currentBranch != null) {
            String remoteTrackingBranch = getRemoteTrackingBranch(currentBranch);
            if (remoteTrackingBranch != null) {
                for (GitRemote remote : remotes) {
                    if (remoteTrackingBranch.startsWith(remote.name() + "/"))
                        return remote;
                }
            }
        }

        Optional<GitRemote> origin = remotes.stream().filter(remote -> remote.name().equals("origin")).findAny();
        if (origin.isPresent())
            return origin.get();

        if (remotes.size() == 1)
            return remotes.getFirst();

        return null;
    }

    /**
     * Schedules current remote update by setting branch upstream.
     *
     * @param remote target remote
     */
    public void setCurrentRemote(GitRemote remote) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                String currentBranch = getCurrentBranch();
                if (currentBranch != null) {
                    String newRemoteBranch = remote.name() + "/" + currentBranch;
                    if (getAllRemoteBranchNames().contains(newRemoteBranch)) {
                        setBranchUpstream(currentBranch, newRemoteBranch);
                    } else {
                        setBranchUpstream(currentBranch, remote.name() + "/HEAD");
                    }
                }
            }
        });
    }

    /**
     * Schedules current branch upstream update.
     *
     * @param branch upstream branch reference
     */
    public void setCurrentUpstreamBranch(String branch) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                String currentBranch = getCurrentBranch();
                if (currentBranch != null) {
                    setBranchUpstream(currentBranch, branch);
                }
            }
        });
    }

    /**
     * Gets commits incoming from upstream to current branch.
     *
     * @return incoming commits
     */
    public List<GitCommit> getIncomingCommits() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        String currentBranch = getCurrentBranch();
        if (currentBranch == null)
            return List.of();

        String remoteTrackingBranch = getRemoteTrackingBranch(currentBranch);
        if (remoteTrackingBranch == null)
            return List.of();

        return this.gitClient.getCommitsBetween(repository, currentBranch, remoteTrackingBranch);
    }

    /**
     * Gets commits outgoing from current branch to upstream.
     *
     * @return outgoing commits
     */
    public List<GitCommit> getOutgoingCommits() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        String currentBranch = getCurrentBranch();
        if (currentBranch == null)
            return List.of();

        String remoteTrackingBranch = getRemoteTrackingBranch(currentBranch);
        if (remoteTrackingBranch == null)
            return List.of();

        return this.gitClient.getCommitsBetween(repository, remoteTrackingBranch, currentBranch);
    }
}
