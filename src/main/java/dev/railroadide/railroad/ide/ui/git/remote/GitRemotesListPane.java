package dev.railroadide.railroad.ide.ui.git.remote;

import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRListView;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.utility.TimeFormatter;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.remote.GitRemote;
import dev.railroadide.railroad.vcs.git.remote.GitUpstream;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.WeakInvalidationListener;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import javafx.util.Duration;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class GitRemotesListPane extends RRListView<GitRemote> {
    private final LongProperty elapsedTick = new SimpleLongProperty();
    private final Timeline elapsedTimeline = new Timeline(
        new KeyFrame(Duration.seconds(1), $ -> elapsedTick.set(elapsedTick.get() + 1))
    );

    public GitRemotesListPane(GitManager gitManager) {
        getStyleClass().add("git-remotes-list");

        elapsedTimeline.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                elapsedTimeline.stop();
            } else {
                elapsedTick.set(0);
                elapsedTimeline.play();
            }
        });

        setCellFactory(ignored -> new GitRemoteListCell(gitManager, elapsedTick));
        setItems(FXCollections.observableArrayList(gitManager.getRemotes()));
        gitManager.repoStatusProperty().addListener((observable, oldValue, newValue) -> {
            List<GitRemote> remotes = gitManager.getRemotes();
            Platform.runLater(() -> getItems().setAll(remotes));
        });
    }

    public void setOnRemoteSelected(Consumer<GitRemote> handler) {
        getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                handler.accept(newVal);
            }
        });
    }

    public static class GitRemoteListCell extends ListCell<GitRemote> {
        private final GitRemoteListItemPane pane;

        public GitRemoteListCell(GitManager gitManager, ReadOnlyLongProperty elapsedTick) {
            this.pane = new GitRemoteListItemPane(gitManager, elapsedTick);
        }

        @Override
        protected void updateItem(GitRemote remote, boolean empty) {
            super.updateItem(remote, empty);
            if (empty || remote == null) {
                setGraphic(null);
                setText(null);
                pane.clear();
            } else {
                pane.setRemote(remote);
                setGraphic(pane);
                setText(null);
            }
        }
    }

    public static class GitRemoteListItemPane extends RRHBox {
        private final GitManager gitManager;
        private final Text remoteNameText = new Text();
        private final LocalizedText lastFetchTimeText = new LocalizedText("railroad.git.remotes.list.fetched_time", "");
        private final Text protocolText = new Text();
        private final LocalizedText urlsCountText = new LocalizedText("railroad.git.remotes.list.urls_count", 0);
        private final RRHBox topInfoBox;
        private final Text upstreamText = new LocalizedText("railroad.git.remotes.list.upstream");
        private GitRemote remote;
        private final InvalidationListener elapsedTickListener = obs -> refreshLastFetchText();

        public GitRemoteListItemPane(GitManager gitManager, ReadOnlyLongProperty elapsedTick) {
            this.gitManager = gitManager;
            getStyleClass().add("git-remote-list-item");

            remoteNameText.getStyleClass().add("git-remote-name");

            lastFetchTimeText.getStyleClass().add("git-remote-last-fetch");

            protocolText.getStyleClass().add("git-remote-protocol");

            urlsCountText.getStyleClass().add("git-remote-urls-count");

            topInfoBox = new RRHBox(2, remoteNameText);
            topInfoBox.getStyleClass().add("git-remote-top-info-box");
            upstreamText.getStyleClass().add("git-remote-upstream");
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

            elapsedTick.addListener(new WeakInvalidationListener(elapsedTickListener));
        }

        public void setRemote(GitRemote remote) {
            this.remote = remote;
            remoteNameText.setText(remote.name());
            protocolText.setText(remote.protocol().name().toLowerCase(Locale.ROOT));
            urlsCountText.setKeyAndArgs("railroad.git.remotes.list.urls_count", gitManager.getRemoteUrls(remote).size());

            boolean isUpstream = gitManager.getUpstream().map(GitUpstream::remoteName).orElse("").equals(remote.name());
            if (isUpstream) {
                if (!topInfoBox.getChildren().contains(upstreamText)) {
                    topInfoBox.getChildren().add(upstreamText);
                }
            } else {
                topInfoBox.getChildren().remove(upstreamText);
            }

            refreshLastFetchText();
        }

        public void clear() {
            this.remote = null;
            remoteNameText.setText(null);
            protocolText.setText(null);
            urlsCountText.setKeyAndArgs("railroad.git.remotes.list.urls_count", 0);
            topInfoBox.getChildren().remove(upstreamText);
            lastFetchTimeText.setKeyAndArgs("railroad.git.remotes.list.fetched_time", "");
        }

        private void refreshLastFetchText() {
            if (remote == null) {
                return;
            }

            lastFetchTimeText.setKeyAndArgs(
                "railroad.git.remotes.list.fetched_time",
                TimeFormatter.formatElapsed(gitManager.getLastFetchTimestamp(remote))
            );
        }
    }
}
