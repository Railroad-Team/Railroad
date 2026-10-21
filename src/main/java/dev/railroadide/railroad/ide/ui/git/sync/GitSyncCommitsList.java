package dev.railroadide.railroad.ide.ui.git.sync;

import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.utility.TimeFormatingUtils;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.ListChangeListener;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.List;

/**
 * Displays incoming or outgoing commits with a localized empty state and elapsed times.
 */
public class GitSyncCommitsList extends RRListView<GitCommit> {
    private final LongProperty elapsedTick = new SimpleLongProperty();
    private final Timeline elapsedTimeline = new Timeline(
        new KeyFrame(Duration.seconds(1), _ -> elapsedTick.set(elapsedTick.get() + 1)));
    private String noCommitsTextKey;

    /**
     * Creates an empty synchronization commit list with elapsed-time refresh.
     */
    public GitSyncCommitsList() {
        getStyleClass().add("git-sync-commits-list");
        setCellFactory(_ -> new GitSyncCommitCell(elapsedTick));

        elapsedTimeline.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                elapsedTimeline.stop();
            } else {
                elapsedTick.set(0);
                elapsedTimeline.play();
            }
        });

        getItems().addListener((ListChangeListener<? super GitCommit>) _ -> updatePlaceholder());
    }

    /**
     * Updates the localized empty-list message and refreshes the placeholder.
     *
     * @param noCommitsTextKey localization key shown when the commit list is empty
     */
    public void setNoCommitsText(String noCommitsTextKey) {
        this.noCommitsTextKey = noCommitsTextKey;
        updatePlaceholder();
    }

    /**
     * Replaces the displayed synchronization commits.
     *
     * @param commits commits to display in list order
     */
    public void setCommits(List<GitCommit> commits) {
        getItems().setAll(commits);
    }

    protected void updatePlaceholder() {
        if (getItems().isEmpty()) {
            setPlaceholder(new LocalizedText(noCommitsTextKey));
        } else {
            setPlaceholder(null);
        }
    }

    /**
     * Reuses a commit row while rendering synchronization commits.
     */
    public static class GitSyncCommitCell extends ListCell<GitCommit> {
        private final GitSyncCommitCellPane pane;

        /**
         * Creates a reusable cell for a synchronization commit.
         *
         * @param elapsedTick observable tick used to refresh relative timestamps
         */
        public GitSyncCommitCell(ReadOnlyLongProperty elapsedTick) {
            getStyleClass().add("git-sync-commit-cell");
            this.pane = new GitSyncCommitCellPane(elapsedTick);
        }

        @Override
        protected void updateItem(GitCommit commit, boolean empty) {
            super.updateItem(commit, empty);
            if (empty || commit == null) {
                setText(null);
                setGraphic(null);
                pane.clear();
            } else {
                setText(null);
                pane.setCommit(commit);
                setGraphic(pane);
            }
        }
    }

    /**
     * Displays a synchronization commit subject, author, and elapsed time.
     */
    public static class GitSyncCommitCellPane extends RRHBox {
        private final Text message = new Text();
        private final Text author = new Text();
        private final Text date = new Text();
        private long commitTimestampMillis = -1L;
        private final InvalidationListener elapsedTickListener = _ -> refreshDate();

        /**
         * Creates a commit summary row connected to elapsed-time updates.
         *
         * @param elapsedTick observable tick used to refresh relative timestamps
         */
        public GitSyncCommitCellPane(ReadOnlyLongProperty elapsedTick) {
            getStyleClass().add("git-sync-commit-cell-pane");

            message.getStyleClass().add("git-sync-commit-message");
            author.getStyleClass().add("git-sync-commit-author");

            var separator = new Text("•");
            separator.getStyleClass().add("git-sync-commit-separator");

            date.getStyleClass().add("git-sync-commit-date");

            var rightBox = new RRHBox(2, author, separator, date);
            rightBox.getStyleClass().add("git-sync-commit-right-box");

            var spacer = new Region();
            getChildren().addAll(message, spacer, rightBox);
            HBox.setHgrow(spacer, Priority.ALWAYS);

            elapsedTick.addListener(new WeakInvalidationListener(elapsedTickListener));
        }

        /**
         * Populates a reusable row with commit subject, author, and elapsed time.
         *
         * @param commit commit to display or act on
         */
        public void setCommit(GitCommit commit) {
            message.setText(commit.subject());
            author.setText(commit.authorName());
            commitTimestampMillis = commit.committerTimestampEpochSeconds() * 1000L;
            refreshDate();
        }

        /**
         * Clears the reusable commit row and resets its timestamp.
         */
        public void clear() {
            message.setText(null);
            author.setText(null);
            date.setText(null);
            commitTimestampMillis = -1L;
        }

        private void refreshDate() {
            if (commitTimestampMillis < 0) {
                date.setText(null);
                return;
            }

            date.setText(TimeFormatingUtils.formatElapsed(commitTimestampMillis));
        }
    }
}
