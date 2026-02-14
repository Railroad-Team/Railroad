package dev.railroadide.railroad.ide.ui.git.remote;

import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRListView;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.railroad.utility.TimeFormatter;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.remote.GitRemote;
import dev.railroadide.railroad.vcs.git.remote.GitUpstream;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.Locale;
import java.util.function.Consumer;

public class GitRemotesListPane extends RRListView<GitRemote> {
    public GitRemotesListPane(GitManager gitManager) {
        getStyleClass().add("git-remotes-list");

        setCellFactory(ignored -> new GitRemoteListCell(gitManager));
        setItems(FXCollections.observableArrayList(gitManager.getRemotes()));
        gitManager.repoStatusProperty().addListener((observable, oldValue, newValue) ->
            getItems().setAll(gitManager.getRemotes()));
    }

    public void setOnRemoteSelected(Consumer<GitRemote> handler) {
        getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                handler.accept(newVal);
            }
        });
    }

    public static class GitRemoteListCell extends ListCell<GitRemote> {
        private final GitManager gitManager;

        public GitRemoteListCell(GitManager gitManager) {
            this.gitManager = gitManager;
        }

        @Override
        protected void updateItem(GitRemote remote, boolean empty) {
            super.updateItem(remote, empty);
            if (empty || remote == null) {
                setGraphic(null);
                setText(null);
            } else {
                setGraphic(new GitRemoteListItemPane(this.gitManager, remote));
                setText(null);
            }
        }
    }

    public static class GitRemoteListItemPane extends RRHBox {
        public GitRemoteListItemPane(GitManager gitManager, GitRemote remote) {
            getStyleClass().add("git-remote-list-item");

            var remoteNameText = new Text(remote.name());
            remoteNameText.getStyleClass().add("git-remote-name");

            var lastFetchTimeText = new LocalizedText("railroad.git.remotes.list.fetched_time", TimeFormatter.formatElapsed(gitManager.getLastFetchTimestamp(remote)));
            lastFetchTimeText.getStyleClass().add("git-remote-last-fetch");
            var fetchElapsedAnimation = new Timeline(
                new KeyFrame(Duration.seconds(1), $ ->
                    lastFetchTimeText.setKeyAndArgs("railroad.git.remotes.list.fetched_time", TimeFormatter.formatElapsed(gitManager.getLastFetchTimestamp(remote))))
            );
            fetchElapsedAnimation.setCycleCount(Timeline.INDEFINITE);
            fetchElapsedAnimation.play();

            var protocolText = new Text(remote.protocol().name().toLowerCase(Locale.ROOT));
            protocolText.getStyleClass().add("git-remote-protocol");

            var urlsCountText = new LocalizedText("railroad.git.remotes.list.urls_count", gitManager.getRemoteUrls(remote).size());
            urlsCountText.getStyleClass().add("git-remote-urls-count");
            gitManager.repoStatusProperty().addListener((observable, oldValue, newValue) -> {
                urlsCountText.setKeyAndArgs("railroad.git.remotes.list.urls_count", gitManager.getRemoteUrls(remote).size());
            });

            var topInfoBox = new RRHBox(2, remoteNameText);
            topInfoBox.getStyleClass().add("git-remote-top-info-box");
            if (gitManager.getUpstream().map(GitUpstream::remoteName).orElse("").equals(remote.name())) {
                var upstreamText = new LocalizedText("railroad.git.remotes.list.upstream");
                upstreamText.getStyleClass().add("git-remote-upstream");
                topInfoBox.getChildren().add(upstreamText);
            }
            topInfoBox.setAlignment(Pos.CENTER_LEFT);

            var bottomInfoBox = new RRHBox(2, lastFetchTimeText, protocolText);
            bottomInfoBox.getStyleClass().add("git-remote-bottom-info-box");
            bottomInfoBox.setAlignment(Pos.CENTER_LEFT);

            var infoBox = new RRVBox(4, topInfoBox, bottomInfoBox);
            infoBox.getStyleClass().add("git-remote-info-box");
            infoBox.setAlignment(Pos.CENTER_LEFT);

            getChildren().addAll(infoBox, urlsCountText);
            HBox.setHgrow(infoBox, Priority.ALWAYS);
            setAlignment(Pos.CENTER_LEFT);
        }
    }
}
