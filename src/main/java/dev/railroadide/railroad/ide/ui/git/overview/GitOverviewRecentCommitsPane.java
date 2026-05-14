package dev.railroadide.railroad.ide.ui.git.overview;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.utility.ShutdownHooks;
import dev.railroadide.railroad.utility.TimeFormatter;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import dev.railroadide.railroad.vcs.git.commit.GitCommitPage;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class GitOverviewRecentCommitsPane extends RRListView<GitCommit> {
    private static final int FALLBACK_COMMIT_COUNT = 5;
    private final AtomicInteger requestedCount = new AtomicInteger(0);
    private final SimpleLongProperty elapsedTick = new SimpleLongProperty();
    private final Timeline elapsedTimeline = new Timeline(
        new KeyFrame(Duration.seconds(1), $ -> elapsedTick.set(elapsedTick.get() + 1))
    );

    public GitOverviewRecentCommitsPane(Project project) {
        getStyleClass().add("git-overview-recent-commits-pane");
        setPlaceholder(new LocalizedText("railroad.git.overview.recent_commits.placeholder"));

        GitManager gitManager = project.getGitManager();
        requestCommits(gitManager, FALLBACK_COMMIT_COUNT);
        setCellFactory(listView -> new GitOverviewRecentCommitCell(elapsedTick));

        elapsedTimeline.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                elapsedTimeline.stop();
            } else {
                elapsedTick.set(0);
                elapsedTimeline.play();
            }
        });

        heightProperty().addListener((obs, oldHeight, newHeight) ->
            updateCommitLimitFromHeight(gitManager));
        skinProperty().addListener((obs, oldSkin, newSkin) ->
            Platform.runLater(() -> updateCommitLimitFromHeight(gitManager)));

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "GitOverviewRecentCommitsPane-Commits-Fetcher");
            thread.setDaemon(true);
            return thread;
        });

        AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        sceneProperty().addListener((obs, oldScene, newScene) ->
            onSceneChanged(newScene, future, executor, gitManager));
        gitManager.lastFetchTimestampProperty().addListener((observable, oldValue, newValue) ->
            requestCommits(gitManager, Math.max(1, requestedCount.get())));

        ShutdownHooks.addHook(executor::shutdownNow);
    }

    private void onSceneChanged(Scene newScene,
                                AtomicReference<ScheduledFuture<?>> futureRef,
                                ScheduledExecutorService executor,
                                GitManager gitManager) {
        ScheduledFuture<?> previousFuture = futureRef.getAndSet(null);
        if (previousFuture != null) {
            previousFuture.cancel(false);
        }

        if (newScene != null) {
            ScheduledFuture<?> newFuture = executor.scheduleAtFixedRate(() ->
                requestCommits(gitManager, Math.max(1, requestedCount.get())), 0, 1, TimeUnit.MINUTES);
            futureRef.set(newFuture);
        }
    }

    private void updateCommitLimitFromHeight(GitManager gitManager) {
        int computed = computeCommitLimit();
        if (computed > 0) {
            requestCommits(gitManager, computed);
        }
    }

    private int computeCommitLimit() {
        double cellHeight = resolveCellHeight();
        if (cellHeight <= 0)
            return -1;

        double availableHeight = getHeight() - snappedTopInset() - snappedBottomInset();
        int count = (int) Math.floor(availableHeight / cellHeight);
        return Math.max(1, count);
    }

    private double resolveCellHeight() {
        double fixed = getFixedCellSize();
        if (fixed > 0)
            return fixed;

        var cell = lookup(".list-cell");
        if (cell != null && cell.getBoundsInParent().getHeight() > 0)
            return cell.getBoundsInParent().getHeight();

        return -1;
    }

    private void requestCommits(GitManager gitManager, int count) {
        int clamped = Math.max(1, count);
        if (requestedCount.getAndSet(clamped) == clamped)
            return;

        gitManager.getRecentCommits(clamped).thenAccept(optCommits ->
            optCommits.map(GitCommitPage::commits).ifPresent(commits ->
                Platform.runLater(() -> getItems().setAll(commits))));
    }

    private static class GitOverviewRecentCommitCell extends ListCell<GitCommit> {
        private final GitOverviewRecentCommitCellPane pane;

        private GitOverviewRecentCommitCell(ReadOnlyLongProperty elapsedTick) {
            this.pane = new GitOverviewRecentCommitCellPane(elapsedTick);
        }

        @Override
        protected void updateItem(GitCommit item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setGraphic(null);
                pane.clear();
            } else {
                pane.setCommit(item);
                setGraphic(pane);
            }
        }
    }

    private static class GitOverviewRecentCommitCellPane extends RRHBox {
        private final Text messageLabel = new Text();
        private final Text authorLabel = new Text();
        private final Text shortHashLabel = new Text();
        private final Text timestampLabel = new Text();
        private final Tooltip authorTooltip = new Tooltip();
        private final Tooltip shortHashTooltip = new Tooltip();
        private final Tooltip timestampTooltip = new Tooltip();
        private long timestampEpochMillis = -1L;
        private final InvalidationListener elapsedTickListener = obs -> refreshTimestamp();

        public GitOverviewRecentCommitCellPane(ReadOnlyLongProperty elapsedTick) {
            getStyleClass().add("git-overview-recent-commit-cell-pane");

            var leftVBox = new RRVBox(2);
            messageLabel.getStyleClass().add("commit-message-label");
            leftVBox.getChildren().add(messageLabel);

            var leftHBox = new RRHBox(5);
            authorLabel.getStyleClass().add("commit-author-label");
            Tooltip.install(authorLabel, authorTooltip);
            leftHBox.getChildren().add(authorLabel);

            shortHashLabel.getStyleClass().add("commit-short-hash-label");
            Tooltip.install(shortHashLabel, shortHashTooltip);
            leftHBox.getChildren().add(shortHashLabel);

            timestampLabel.getStyleClass().add("commit-timestamp-label");
            Tooltip.install(timestampLabel, timestampTooltip);

            leftVBox.getChildren().add(leftHBox);
            getChildren().add(leftVBox);
            getChildren().add(timestampLabel);
            HBox.setHgrow(leftVBox, Priority.ALWAYS);

            elapsedTick.addListener(new WeakInvalidationListener(elapsedTickListener));
        }

        public void setCommit(GitCommit commit) {
            messageLabel.setText(commit.subject());
            authorLabel.setText(commit.authorName());
            authorTooltip.setText(commit.authorEmail());
            shortHashLabel.setText(commit.shortHash());
            shortHashTooltip.setText(commit.hash());
            timestampEpochMillis = commit.authorTimestampEpochSeconds() * 1000L;
            timestampTooltip.setText(TimeFormatter.formatDateTime(timestampEpochMillis));
            refreshTimestamp();
        }

        public void clear() {
            timestampEpochMillis = -1L;
            messageLabel.setText(null);
            authorLabel.setText(null);
            authorTooltip.setText(null);
            shortHashLabel.setText(null);
            shortHashTooltip.setText(null);
            timestampLabel.setText(null);
            timestampTooltip.setText(null);
        }

        private void refreshTimestamp() {
            if (timestampEpochMillis < 0L) {
                timestampLabel.setText(null);
                return;
            }

            timestampLabel.setText(TimeFormatter.formatElapsed(timestampEpochMillis));
        }
    }
}
