package dev.railroadide.railroad.vcs.git;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.project.Project;
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
        this.gitClient.setGitExecutable(path);
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
                this.remoteFetchTimestamps.put(getUpstream().map(GitUpstream::remoteName).orElse(""), System.currentTimeMillis());
                refreshStatusInternal();
            }
        });
    }

    public ObservableValue<Long> lastFetchTimestampProperty() {
        return remoteFetchTimestamps.map(map -> map.values().stream().max(Long::compareTo).orElse(0L));
    }

    public long getLastFetchTimestamp() {
        return lastFetchTimestampProperty().getValue();
    }

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
        return this.gitClient.getUnstagedDiffText(repository, relativePath);
    }

    public String getHeadCommitHash() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        return this.gitClient.getHeadCommitHash(repository);
    }

    public List<String> getTagsPointingToCommit(String hash) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getTagsPointingToCommit(repository, hash);
    }

    public Map<String, List<String>> getTagsByCommit() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return Map.of();

        return this.gitClient.getTagsByCommit(repository);
    }

    public List<String> getAllBranchNames() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getAllBranches(repository);
    }

    public List<String> getAllLocalBranchNames() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getAllLocalBranches(repository);
    }

    public List<String> getAllRemoteBranchNames() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getAllRemoteBranches(repository);
    }

    public List<GitAuthor> getAllAuthors(boolean includeEmail) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return List.of();

        return this.gitClient.getAllAuthors(repository, includeEmail);
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

        return this.gitClient.getRepositoryCreationDate(repository);
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

    public boolean hasUncommittedChanges(String branchName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return false;

        return this.gitClient.hasUncommittedChanges(repository, branchName);
    }

    public String getCurrentBranch() {
        return Optional.ofNullable(getRepoStatus())
            .map(GitRepoStatus::branch)
            .orElse(null);
    }

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

    public String getRemoteTrackingBranch(String localBranchName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        return this.gitClient.getRemoteTrackingBranch(repository, localBranchName);
    }

    public int[] getAheadBehindCounts(String localBranchName, String remoteBranchName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return new int[]{0, 0};

        return this.gitClient.getAheadBehindCounts(repository, localBranchName, remoteBranchName);
    }

    public String getLastCommitHash(String branchName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        return this.gitClient.getLastCommitHash(repository, branchName);
    }

    public Long getLastCommitTimestamp(String branchName) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        return this.gitClient.getLastCommitTimestamp(repository, branchName);
    }

    public String getCommitMessage(String hash) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || hash == null || hash.isBlank())
            return null;

        return this.gitClient.getCommitMessage(repository, hash).lines().findFirst().orElse(null);
    }

    public GitAuthor getCommitAuthor(String hash) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || hash == null || hash.isBlank())
            return null;

        return this.gitClient.getCommitAuthor(repository, hash);
    }

    public void setBranchUpstream(String localBranchName, String remoteBranchName) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.setBranchUpstream(repository, localBranchName, remoteBranchName);
                refreshStatusInternal();
            }
        });
    }

    public void unsetBranchUpstream(String localBranchName) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.unsetBranchUpstream(repository, localBranchName);
                refreshStatusInternal();
            }
        });
    }

    public void deleteBranch(String branchName, boolean force) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.deleteBranch(repository, branchName, force);
                refreshStatusInternal();
            }
        });
    }

    public void renameBranch(String oldName, String newName, boolean force) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.renameBranch(repository, oldName, newName, force);
                refreshStatusInternal();
            }
        });
    }

    public List<String> getRemoteUrls(GitRemote remote) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || remote == null)
            return List.of();

        return this.gitClient.getRemoteUrls(repository, remote);
    }

    public boolean isPruningEnabled(GitRemote remote) {
        GitRepository repository = this.gitRepository.get();
        if (repository == null || remote == null)
            return false;

        return this.gitClient.isPruningEnabled(repository, remote);
    }

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

    public void addRemote(String name, String fetchUrl, String pushUrl) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.addRemote(repository, name, fetchUrl, pushUrl);
                refreshStatusInternal();
            }
        });
    }

    public void updateRemote(String oldName, String newName, String fetchUrl, String pushUrl) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.updateRemote(repository, oldName, newName, fetchUrl, pushUrl);
                refreshStatusInternal();
            }
        });
    }

    public void removeRemote(String name) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.removeRemote(repository, name);
                refreshStatusInternal();
            }
        });
    }

    public GitPullStrategy getPullStrategy() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        return this.gitClient.getPullStrategy(repository, getCurrentBranch());
    }

    public GitPushStrategy getPushStrategy() {
        GitRepository repository = this.gitRepository.get();
        if (repository == null)
            return null;

        return this.gitClient.getPushStrategy(repository);
    }

    public void setPushStrategy(GitPushStrategy strategy) {
        this.executorService.submit(() -> {
            GitRepository repository = this.gitRepository.get();
            if (repository != null) {
                this.gitClient.setPushStrategy(repository, strategy, getCurrentBranch());
                refreshStatusInternal();
            }
        });
    }

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
