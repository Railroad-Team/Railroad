package dev.railroadide.railroad.ide.ui.git.remote;

import dev.railroadide.core.ui.RRGridPane;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.utility.TimeFormatter;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.remote.GitRemote;
import dev.railroadide.railroad.vcs.git.remote.GitUpstream;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Duration;

import java.util.Optional;

public class GitRemoteDetailsPane extends RRVBox {
    private final ObjectProperty<GitRemote> remote = new SimpleObjectProperty<>();
    private final GitManager gitManager;

    private final GridPane detailsGrid = new RRGridPane();
    private final Text remoteNameText = new Text();
    private final Text fetchUrlText = new Text();
    private final Text pushUrlText = new Text();
    private final Text defaultBranchText = new Text();
    private final Text lastFetchedText = new Text();
    private final Text pruningEnabledText = new Text();
    private final Timeline fetchElapsedAnimation;

    public GitRemoteDetailsPane(GitManager gitManager, GitRemote remote) {
        this.gitManager = gitManager;

        getStyleClass().add("git-remote-details-pane");
        setAlignment(Pos.TOP_LEFT);

        configureGrid();

        this.remote.addListener((observable, oldValue, newValue) -> updateContent(newValue));
        this.remote.set(remote);

        fetchElapsedAnimation = new Timeline(new KeyFrame(Duration.seconds(1), $ -> refreshLastFetchedText()));
        fetchElapsedAnimation.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                fetchElapsedAnimation.stop();
            } else {
                refreshLastFetchedText();
                fetchElapsedAnimation.play();
            }
        });

        if (this.gitManager != null) {
            this.gitManager.repoStatusProperty().addListener((obs, oldStatus, newStatus) -> updateContent(this.remote.get()));
        }
    }

    public void displayRemote(GitRemote remote) {
        this.remote.set(remote);
    }

    private void configureGrid() {
        detailsGrid.getStyleClass().add("git-remote-details-grid");
        detailsGrid.setHgap(12);
        detailsGrid.setVgap(0);

        var col1 = new ColumnConstraints();
        col1.setHgrow(Priority.NEVER);
        col1.setPrefWidth(Region.USE_COMPUTED_SIZE);
        col1.setMinWidth(Region.USE_PREF_SIZE);
        col1.setMaxWidth(Region.USE_PREF_SIZE);
        var col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        detailsGrid.getColumnConstraints().addAll(col1, col2);

        remoteNameText.getStyleClass().add("git-remote-details-value-text");
        fetchUrlText.getStyleClass().add("git-remote-details-value-text");
        pushUrlText.getStyleClass().add("git-remote-details-value-text");
        defaultBranchText.getStyleClass().add("git-remote-details-value-text");
        lastFetchedText.getStyleClass().add("git-remote-details-value-text");
        pruningEnabledText.getStyleClass().add("git-remote-details-value-text");

        int row = 0;
        addRow("railroad.git.remotes.details.name.label", remoteNameText, row++);
        addSeparator(row++);
        addRow("railroad.git.remotes.details.fetch_url.label", fetchUrlText, row++);
        addSeparator(row++);
        addRow("railroad.git.remotes.details.push_url.label", pushUrlText, row++);
        addSeparator(row++);
        addRow("railroad.git.remotes.details.default_branch.label", defaultBranchText, row++);
        addSeparator(row++);
        addRow("railroad.git.remotes.details.last_fetched.label", lastFetchedText, row++);
        addSeparator(row++);
        addRow("railroad.git.remotes.details.pruning.label", pruningEnabledText, row);

        VBox.setVgrow(detailsGrid, Priority.ALWAYS);
    }

    private void addRow(String labelKey, Text value, int row) {
        var labelNode = new LocalizedText(labelKey);
        labelNode.getStyleClass().addAll("localized-text", "git-remote-details-label");

        detailsGrid.add(labelNode, 0, row);
        GridPane.setValignment(labelNode, VPos.CENTER);

        var valueFlow = new TextFlow(value);
        detailsGrid.add(valueFlow, 1, row);
        GridPane.setValignment(valueFlow, VPos.CENTER);
    }

    private void addSeparator(int row) {
        var separator = new Region();
        separator.getStyleClass().add("git-remote-details-row-separator");
        separator.setMaxWidth(Double.MAX_VALUE);
        GridPane.setMargin(separator, new Insets(4, 0, 4, 0));
        detailsGrid.add(separator, 0, row, 2, 1);
    }

    private void updateContent(GitRemote remote) {
        Platform.runLater(() -> {
            getChildren().clear();

            if (remote == null) {
                var emptyState = new LocalizedText("railroad.git.remotes.details.no_remote_selected");
                emptyState.getStyleClass().add("git-remote-details-empty-state");
                getChildren().add(emptyState);
                setAlignment(Pos.CENTER);
                return;
            }

            boolean isDefaultRemote = false;
            String defaultBranch = L18n.localize("railroad.git.remotes.details.not_available");

            if (gitManager != null) {
                Optional<GitUpstream> upstreamOpt = gitManager.getUpstream();
                if (upstreamOpt.isPresent()) {
                    GitUpstream upstream = upstreamOpt.get();
                    isDefaultRemote = remote.name().equals(upstream.remoteName());
                    if (isDefaultRemote && upstream.branchName() != null && !upstream.branchName().isBlank()) {
                        defaultBranch = upstream.branchName();
                    }
                }

                long lastFetchTimestamp = gitManager.getLastFetchTimestamp(remote);
                if (lastFetchTimestamp > 0L) {
                    lastFetchedText.setText(TimeFormatter.formatElapsed(lastFetchTimestamp));
                } else {
                    lastFetchedText.setText(L18n.localize("railroad.git.remotes.details.never"));
                }
            } else {
                lastFetchedText.setText(L18n.localize("railroad.git.remotes.details.never"));
            }

            remoteNameText.setText(isDefaultRemote
                ? String.format("%s (%s)", remote.name(), L18n.localize("railroad.git.remotes.details.default_suffix"))
                : remote.name());
            fetchUrlText.setText(remote.fetchUrl() == null || remote.fetchUrl().isBlank()
                ? L18n.localize("railroad.git.remotes.details.not_available")
                : remote.fetchUrl());
            pushUrlText.setText(remote.pushUrl() == null || remote.pushUrl().isBlank()
                ? L18n.localize("railroad.git.remotes.details.not_available")
                : remote.pushUrl());
            defaultBranchText.setText(defaultBranch);
            pruningEnabledText.setText(gitManager != null
                ? L18n.localize("railroad.git.remotes.details.pruning.enabled")
                : L18n.localize("railroad.git.remotes.details.unknown"));

            getChildren().add(detailsGrid);
            setAlignment(Pos.TOP_LEFT);
        });
    }

    public ObjectProperty<GitRemote> remoteProperty() {
        return remote;
    }

    private void refreshLastFetchedText() {
        GitRemote currentRemote = this.remote.get();
        if (currentRemote == null || gitManager == null) {
            return;
        }

        long lastFetchTimestamp = gitManager.getLastFetchTimestamp(currentRemote);
        if (lastFetchTimestamp > 0L) {
            lastFetchedText.setText(TimeFormatter.formatElapsed(lastFetchTimestamp));
        } else {
            lastFetchedText.setText(L18n.localize("railroad.git.remotes.details.never"));
        }
    }
}
