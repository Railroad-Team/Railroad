package dev.railroadide.railroad.ide.ui.git.sync;

import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRListView;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.railroad.utility.TimeFormatter;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.List;

public class GitSyncCommitsList extends RRListView<GitCommit> {
    private String noCommitsTextKey;

    public GitSyncCommitsList() {
        getStyleClass().add("git-sync-commits-list");
        setCellFactory(listView -> new GitSyncCommitCell());

        getItems().addListener((ListChangeListener<? super GitCommit>) change -> updatePlaceholder());
    }

    public void setNoCommitsText(String noCommitsTextKey) {
        this.noCommitsTextKey = noCommitsTextKey;
        updatePlaceholder();
    }

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

    public static class GitSyncCommitCell extends ListCell<GitCommit> {
        public GitSyncCommitCell() {
            getStyleClass().add("git-sync-commit-cell");
        }

        @Override
        protected void updateItem(GitCommit commit, boolean empty) {
            super.updateItem(commit, empty);
            if (empty || commit == null) {
                setText(null);
                setGraphic(null);
            } else {
                setText(null);
                setGraphic(new GitSyncCommitCellPane(commit));
            }
        }
    }

    public static class GitSyncCommitCellPane extends RRHBox {
        public GitSyncCommitCellPane(GitCommit commit) {
            getStyleClass().add("git-sync-commit-cell-pane");

            var message = new LocalizedText(commit.subject());
            message.getStyleClass().add("git-sync-commit-message");

            var author = new LocalizedText(commit.authorName());
            author.getStyleClass().add("git-sync-commit-author");

            var separator = new Text("•");
            separator.getStyleClass().add("git-sync-commit-separator");

            var date = new Text(TimeFormatter.formatElapsed(commit.committerTimestampEpochSeconds() * 1000L));
            date.getStyleClass().add("git-sync-commit-date");
            var timeline = new Timeline(
                new KeyFrame(Duration.seconds(1), $ ->
                    date.setText(TimeFormatter.formatElapsed(commit.committerTimestampEpochSeconds() * 1000L)))
            );
            timeline.setCycleCount(Timeline.INDEFINITE);
            timeline.play();

            var rightBox = new RRHBox(2, author, separator, date);
            rightBox.getStyleClass().add("git-sync-commit-right-box");

            getChildren().addAll(message, rightBox);
            HBox.setHgrow(message, Priority.ALWAYS);
        }
    }
}
