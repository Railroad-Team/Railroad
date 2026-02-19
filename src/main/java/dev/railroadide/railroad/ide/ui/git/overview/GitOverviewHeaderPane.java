package dev.railroadide.railroad.ide.ui.git.overview;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.*;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.utility.TimeFormatter;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.remote.GitRemote;
import dev.railroadide.railroad.vcs.git.remote.GitUpstream;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import dev.railroadide.railroad.vcs.git.status.GitRepoStatus;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.List;

public class GitOverviewHeaderPane extends RRVBox {
    private final RRHBox actionsBox = new RRHBox(8);
    private final GridPane infoGrid = new RRGridPane();
    private final RRHBox changesRow = new RRHBox(10);
    private final ChangeChip stagedChip = new ChangeChip("Staged");
    private final ChangeChip unstagedChip = new ChangeChip("Unstaged");
    private final ChangeChip untrackedChip = new ChangeChip("Untracked");
    private final ChangeChip conflictedChip = new ChangeChip("Conflicted");

    private final Text repoNameText = new Text();
    private final HBox repoStatusTag = createTag("git-overview-tag");
    private final Text repoStatusText = new Text();
    private final Text headBranchText = new Text();
    private final HBox headUpstreamTag = createTag("git-overview-tag");
    private final Text headUpstreamText = new Text();
    private final HBox upstreamBehindTag = createTag("git-overview-tag", "git-overview-tag-warn");
    private final Text upstreamBehindText = new Text();
    private final HBox upstreamAheadTag = createTag("git-overview-tag", "git-overview-tag-good");
    private final Text upstreamAheadText = new Text();
    private final HBox upstreamFetchTag = createTag("git-overview-tag");
    private final Text upstreamFetchText = new Text();
    private final Text remoteNameText = new Text();
    private final Text remoteUrlText = new Text();
    private final Timeline upstreamElapsedTimeline;
    private final GitManager gitManager;

    public GitOverviewHeaderPane(Project project) {
        getStyleClass().add("git-overview-header-pane");
        setSpacing(8);

        // Actions Box
        actionsBox.getStyleClass().add("git-overview-actions-box");
        actionsBox.setAlignment(Pos.CENTER);
        var fetchButton = new RRButton("railroad.git.overview.header.fetch.button", FontAwesomeSolid.SYNC_ALT);
        fetchButton.setVariant(ButtonVariant.PRIMARY);
        fetchButton.setOnAction($ -> project.getGitManager().fetch());
        actionsBox.getChildren().add(fetchButton);

        var pullButton = new RRButton("railroad.git.overview.header.pull.button", FontAwesomeSolid.DOWNLOAD);
        pullButton.setVariant(ButtonVariant.PRIMARY);
        pullButton.setOnAction($ -> project.getGitManager().pull());
        actionsBox.getChildren().add(pullButton);

        var pushButton = new RRButton("railroad.git.overview.header.push.button", FontAwesomeSolid.PAPER_PLANE);
        pushButton.setVariant(ButtonVariant.PRIMARY);
        pushButton.setOnAction($ -> project.getGitManager().push());
        actionsBox.getChildren().add(pushButton);

        getChildren().add(actionsBox);

        configureInfoGrid();
        VBox.setVgrow(infoGrid, Priority.ALWAYS);
        getChildren().add(infoGrid);
        configureChangeChips();
        getChildren().add(changesRow);

        gitManager = project.getGitManager();
        updateHeaderInfo(gitManager);
        listenForUpdates(gitManager);

        upstreamElapsedTimeline = new Timeline(new KeyFrame(Duration.seconds(1), $ -> updateUpstreamRow(this.gitManager)));
        upstreamElapsedTimeline.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                upstreamElapsedTimeline.stop();
            } else {
                updateUpstreamRow(this.gitManager);
                upstreamElapsedTimeline.play();
            }
        });
    }

    private void configureInfoGrid() {
        infoGrid.getStyleClass().add("git-overview-info-grid");
        infoGrid.setHgap(12);
        infoGrid.setVgap(0); // Set vgap to 0 because separators will provide vertical spacing

        var col1 = new ColumnConstraints();
        col1.setHgrow(Priority.NEVER);
        col1.setPrefWidth(Region.USE_COMPUTED_SIZE);
        col1.setMinWidth(Region.USE_PREF_SIZE);
        col1.setMaxWidth(Region.USE_PREF_SIZE);
        var col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        infoGrid.getColumnConstraints().addAll(col1, col2);

        int row = 0;

        // Repository
        repoNameText.getStyleClass().addAll("git-overview-table-value-text", "git-overview-value-strong");
        repoStatusText.getStyleClass().add("git-overview-table-value-text");
        repoStatusTag.getChildren().addAll(createDot(), repoStatusText);
        infoGrid.add(new LocalizedText("railroad.git.overview.header.repository.label"), 0, row);
        GridPane.setValignment(infoGrid.getChildren().getLast(), VPos.CENTER);
        infoGrid.add(new RRFlowPane(6, 6, repoNameText, repoStatusTag), 1, row);
        GridPane.setValignment(infoGrid.getChildren().getLast(), VPos.CENTER);
        row++;
        Region separator0 = new Region();
        separator0.getStyleClass().add("git-overview-grid-row-separator");
        separator0.setMaxWidth(Double.MAX_VALUE); // Ensure the separator stretches
        GridPane.setMargin(separator0, new Insets(4, 0, 4, 0)); // Add vertical margin for separator
        infoGrid.add(separator0, 0, row, 2, 1); // col=0, row=current, columnspan=2, rowspan=1
        row++;

        // HEAD
        headBranchText.getStyleClass().addAll("git-overview-table-value-text", "git-overview-mono");
        var headUpstreamLabel = new Text("upstream:");
        headUpstreamLabel.getStyleClass().add("git-overview-table-value-text");
        headUpstreamText.getStyleClass().addAll("git-overview-table-value-text", "git-overview-mono");
        headUpstreamTag.getChildren().addAll(headUpstreamLabel, headUpstreamText);
        infoGrid.add(new LocalizedText("railroad.git.overview.header.head.label"), 0, row);
        GridPane.setValignment(infoGrid.getChildren().getLast(), VPos.CENTER);
        infoGrid.add(new RRFlowPane(6, 6, headBranchText, headUpstreamTag), 1, row);
        GridPane.setValignment(infoGrid.getChildren().getLast(), VPos.CENTER);
        row++;
        Region separator1 = new Region();
        separator1.getStyleClass().add("git-overview-grid-row-separator");
        separator1.setMaxWidth(Double.MAX_VALUE); // Ensure the separator stretches
        GridPane.setMargin(separator1, new Insets(4, 0, 4, 0)); // Add vertical margin for separator
        infoGrid.add(separator1, 0, row, 2, 1);
        row++;

        // Upstream
        upstreamBehindText.getStyleClass().addAll("git-overview-table-value-text", "git-overview-value-strong");
        var upstreamBehindLabel = new Text("behind");
        upstreamBehindLabel.getStyleClass().add("git-overview-table-value-text");
        upstreamBehindTag.getChildren().addAll(createDot("git-overview-dot-warn"), upstreamBehindLabel, upstreamBehindText);

        upstreamAheadText.getStyleClass().addAll("git-overview-table-value-text", "git-overview-value-strong");
        var upstreamAheadLabel = new Text("ahead");
        upstreamAheadLabel.getStyleClass().add("git-overview-table-value-text");
        upstreamAheadTag.getChildren().addAll(createDot("git-overview-dot-good"), upstreamAheadLabel, upstreamAheadText);

        upstreamFetchText.getStyleClass().addAll("git-overview-table-value-text", "git-overview-mono");
        var upstreamFetchLabel = new Text("last fetch");
        upstreamFetchLabel.getStyleClass().add("git-overview-table-value-text");
        upstreamFetchTag.getChildren().addAll(upstreamFetchLabel, upstreamFetchText);
        infoGrid.add(new LocalizedText("railroad.git.overview.header.upstream.label"), 0, row);
        GridPane.setValignment(infoGrid.getChildren().getLast(), VPos.CENTER);
        infoGrid.add(new RRFlowPane(6, 6, upstreamBehindTag, upstreamAheadTag, upstreamFetchTag), 1, row);
        GridPane.setValignment(infoGrid.getChildren().getLast(), VPos.CENTER);
        row++;
        Region separator2 = new Region();
        separator2.getStyleClass().add("git-overview-grid-row-separator");
        separator2.setMaxWidth(Double.MAX_VALUE); // Ensure the separator stretches
        GridPane.setMargin(separator2, new Insets(4, 0, 4, 0)); // Add vertical margin for separator
        infoGrid.add(separator2, 0, row, 2, 1);
        row++;

        // Remote
        remoteNameText.getStyleClass().addAll("git-overview-table-value-text", "git-overview-mono");
        var remoteSeparatorText = new Text("•");
        remoteSeparatorText.getStyleClass().add("git-overview-separator");
        remoteUrlText.getStyleClass().addAll("git-overview-table-value-text", "git-overview-code");
        infoGrid.add(new LocalizedText("railroad.git.overview.header.remote.label"), 0, row);
        GridPane.setValignment(infoGrid.getChildren().getLast(), VPos.CENTER);
        infoGrid.add(new RRFlowPane(6, 6, remoteNameText, remoteSeparatorText, remoteUrlText), 1, row);
        GridPane.setValignment(infoGrid.getChildren().getLast(), VPos.CENTER);
    }

    private void updateHeaderInfo(GitManager gitManager) {
        GitRepoStatus status = gitManager.getRepoStatus();
        if (status == null) {
            repoNameText.setText("Unknown");
            repoStatusText.setText("Unknown");
            updateStatusTag(repoStatusTag, "Unknown");
            headBranchText.setText("Unknown");
            headUpstreamText.setText("None");
            upstreamBehindText.setText("0");
            upstreamAheadText.setText("0");
            upstreamFetchText.setText("Never");
            remoteNameText.setText("None");
            remoteUrlText.setText("N/A");
            stagedChip.setCount(0);
            unstagedChip.setCount(0);
            untrackedChip.setCount(0);
            conflictedChip.setCount(0);
            return;
        }
        List<GitFileChange> changes = status.changes();
        boolean dirty = !changes.isEmpty();

        String repositoryName = gitManager.getGitRepository().root().getFileName().toString();
        repoNameText.setText(repositoryName);
        repoStatusText.setText(dirty ? "Dirty" : "Clean");
        updateStatusTag(repoStatusTag, dirty ? "Dirty" : "Clean");

        GitUpstream upstream = gitManager.getUpstream().orElse(null);

        String upstreamTarget = upstream != null ? upstream.remoteName() + "/" + upstream.branchName() : "None";
        headBranchText.setText(status.branch());
        headUpstreamText.setText(upstreamTarget);

        updateUpstreamRow(gitManager);

        String remoteName = upstream != null ? upstream.remoteName() : "None";
        String remoteUrl = gitManager.getRemotes().stream()
            .filter(r -> upstream != null && r.name().equals(upstream.remoteName()))
            .findFirst()
            .map(GitRemote::fetchUrl)
            .orElse("N/A");
        remoteNameText.setText(remoteName);
        remoteUrlText.setText(remoteUrl);

        long staged = changes.stream().filter(GitFileChange::isStaged).count();
        long unstaged = changes.stream().filter(GitFileChange::isUnstaged).count();
        long untracked = changes.stream().filter(GitFileChange::isUntracked).count();
        long conflicted = changes.stream().filter(GitFileChange::isConflict).count();
        stagedChip.setCount(staged);
        unstagedChip.setCount(unstaged);
        untrackedChip.setCount(untracked);
        conflictedChip.setCount(conflicted);
    }

    private void listenForUpdates(GitManager gitManager) {
        gitManager.repoStatusProperty().addListener((obs, oldStatus, newStatus) ->
            updateHeaderInfo(gitManager));
    }

    private void updateUpstreamRow(GitManager gitManager) {
        var status = gitManager.getRepoStatus();
        if (status == null) {
            upstreamBehindText.setText("0");
            upstreamAheadText.setText("0");
            upstreamFetchText.setText("Never");
            return;
        }
        upstreamBehindText.setText(Long.toString(status.behind()));
        upstreamAheadText.setText(Long.toString(status.ahead()));
        upstreamFetchText.setText(TimeFormatter.formatElapsed(gitManager.getLastFetchTimestamp()));
    }

    private void configureChangeChips() {
        changesRow.getStyleClass().add("git-overview-changes-row");
        changesRow.getChildren().addAll(stagedChip, unstagedChip, untrackedChip, conflictedChip);
        changesRow.setAlignment(Pos.CENTER);
    }

    private static HBox createTag(String... styleClasses) {
        var tag = new RRHBox(4);
        tag.getStyleClass().addAll(styleClasses);
        return tag;
    }

    private static Text createDot(String... extraClasses) {
        var dot = new Text("•");
        dot.getStyleClass().add("git-overview-dot");
        dot.getStyleClass().addAll(extraClasses);
        return dot;
    }

    private static void updateStatusTag(HBox tag, String status) {
        tag.getStyleClass().removeAll("git-overview-tag-warn", "git-overview-tag-good");
        if ("Dirty".equalsIgnoreCase(status)) {
            tag.getStyleClass().add("git-overview-tag-warn");
        } else {
            tag.getStyleClass().add("git-overview-tag-good");
        }
    }

    private static class ChangeChip extends RRHBox {
        private final Text countText = new Text();

        private ChangeChip(String label) {
            super(4);
            getStyleClass().add("git-overview-change-chip");
            var labelText = new Text(label);
            countText.getStyleClass().add("git-overview-change-number");
            labelText.getStyleClass().add("git-overview-change-label");
            getChildren().addAll(countText, labelText);
            setCount(0);
        }

        private void setCount(long count) {
            countText.setText(Long.toString(count));
        }
    }
}
