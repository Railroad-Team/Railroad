package dev.railroadide.railroad.ide.ui.git.branches;

import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.utility.TimeFormatter;
import dev.railroadide.railroad.vcs.git.branch.GitBranch;
import dev.railroadide.railroad.vcs.git.branch.GitBranchLastCommit;
import dev.railroadide.railroad.vcs.git.branch.GitBranchStatus;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Locale;

public class GitLocalBranchesListView extends AbstractGitBranchesListView<GitBranch.LocalGitBranch> {
    private final Timeline elapsedRefreshTimeline = new Timeline(new KeyFrame(Duration.seconds(1), $ -> refresh()));

    public GitLocalBranchesListView(RailroadProject project) {
        super(
            project,
            "git-local-branches-list-view",
            value -> value.getGitManager().getAllLocalBranches(),
            listView -> new GitLocalBranchCell()
        );

        elapsedRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                elapsedRefreshTimeline.stop();
            } else {
                refresh();
                elapsedRefreshTimeline.play();
            }
        });
    }

    @Override
    Node createDetailsNode(GitBranch.LocalGitBranch branch) {
        var rows = new ArrayList<Node>();
        String upstream = branch.remoteName();
        if (upstream == null || upstream.isBlank()) {
            rows.add(createLocalizedDetailsRow("railroad.git.branches.details.upstream", "railroad.git.branches.no_upstream"));
        } else {
            rows.add(createTextDetailsRow("railroad.git.branches.details.upstream", upstream));
        }
        rows.add(createLocalizedDetailsRow(
            "railroad.git.branches.details.current",
            branch.isCurrent() ? "railroad.git.branches.details.current.yes" : "railroad.git.branches.details.current.no"
        ));
        rows.add(createLocalizedDetailsRow("railroad.git.branches.details.ahead", "railroad.git.branches.local.ahead", branch.aheadCount()));
        rows.add(createLocalizedDetailsRow("railroad.git.branches.details.behind", "railroad.git.branches.local.behind", branch.behindCount()));
        return createCommonDetailsNode(branch, rows);
    }

    private static class GitLocalBranchCell extends ListCell<GitBranch.LocalGitBranch> {
        private final HBox root = new RRHBox(8);
        private final VBox details = new RRVBox(2);
        private final HBox titleRow = new RRHBox(6);
        private final HBox metadataRow = new RRHBox(6);

        private final Label branchNameLabel = new Label();
        private final LocalizedLabel currentBranchLabel = new LocalizedLabel("railroad.git.branches.head");
        private final LocalizedLabel upstreamChipLabel = new LocalizedLabel("");
        private final LocalizedLabel lastSummaryLabel = new LocalizedLabel("");
        private final LocalizedLabel statusLabel = new LocalizedLabel("");

        private final HBox aheadChip = new RRHBox(4);
        private final Circle aheadDot = new Circle(3);
        private final LocalizedLabel aheadLabel = new LocalizedLabel("");

        private final HBox behindChip = new RRHBox(4);
        private final Circle behindDot = new Circle(3);
        private final LocalizedLabel behindLabel = new LocalizedLabel("");
        private String statusStyleClass;

        private GitLocalBranchCell() {
            getStyleClass().add("git-local-branch-list-cell");

            root.getStyleClass().add("git-local-branch-cell");
            details.getStyleClass().add("git-local-branch-details");
            titleRow.getStyleClass().add("git-local-branch-title-row");
            metadataRow.getStyleClass().add("git-local-branch-metadata-row");
            root.setAlignment(Pos.CENTER_LEFT);

            branchNameLabel.getStyleClass().add("git-local-branch-name");
            currentBranchLabel.getStyleClass().add("git-local-branch-current-badge");
            upstreamChipLabel.getStyleClass().add("git-local-branch-upstream-chip");
            lastSummaryLabel.getStyleClass().add("git-local-branch-last-summary");
            statusLabel.getStyleClass().add("git-local-branch-status");

            aheadChip.getStyleClass().addAll("git-local-branch-chip", "git-local-branch-ahead-chip");
            aheadDot.getStyleClass().add("git-local-branch-chip-dot");
            aheadLabel.getStyleClass().add("git-local-branch-chip-label");
            aheadChip.getChildren().addAll(aheadDot, aheadLabel);

            behindChip.getStyleClass().addAll("git-local-branch-chip", "git-local-branch-behind-chip");
            behindDot.getStyleClass().add("git-local-branch-chip-dot");
            behindLabel.getStyleClass().add("git-local-branch-chip-label");
            behindChip.getChildren().addAll(behindDot, behindLabel);

            HBox.setHgrow(details, Priority.ALWAYS);
            HBox.setHgrow(lastSummaryLabel, Priority.ALWAYS);

            titleRow.getChildren().addAll(branchNameLabel, currentBranchLabel, upstreamChipLabel);
            metadataRow.getChildren().addAll(lastSummaryLabel, aheadChip, behindChip);
            details.getChildren().addAll(titleRow, metadataRow);
            root.getChildren().addAll(details, statusLabel);
        }

        @Override
        protected void updateItem(GitBranch.LocalGitBranch item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                branchNameLabel.setText(null);
                upstreamChipLabel.setText(null);
                lastSummaryLabel.setText(null);
                lastSummaryLabel.setTooltip(null);
                aheadLabel.setText(null);
                behindLabel.setText(null);
                statusLabel.setText(null);
                setText(null);
                setGraphic(null);
                return;
            }

            branchNameLabel.setText(item.name());
            currentBranchLabel.setVisible(item.isCurrent());
            currentBranchLabel.setManaged(item.isCurrent());

            String upstream = item.remoteName();
            boolean hasUpstream = upstream != null && !upstream.isBlank();
            upstreamChipLabel.setVisible(true);
            upstreamChipLabel.setManaged(true);
            if (hasUpstream) {
                upstreamChipLabel.setText(upstream);
            } else {
                upstreamChipLabel.setKey("railroad.git.branches.no_upstream");
            }

            GitBranchLastCommit lastCommit = item.lastCommit();
            String fullHash = lastCommit == null ? null : lastCommit.hash();
            boolean hasHash = fullHash != null && !fullHash.isBlank();
            String shortHash = hasHash && fullHash.length() > 8 ? fullHash.substring(0, 8) : fullHash;

            Long timestampSeconds = lastCommit == null ? null : lastCommit.timestampEpochSeconds();
            if (hasHash) {
                if (timestampSeconds == null) {
                    lastSummaryLabel.setKey("railroad.git.branches.local.last_summary_never", shortHash);
                } else {
                    String lastCommitAge = TimeFormatter.formatElapsed(timestampSeconds * 1000L);
                    lastSummaryLabel.setKey("railroad.git.branches.local.last_summary", shortHash, lastCommitAge);
                }
            } else {
                lastSummaryLabel.setKey("railroad.git.branches.local.last_summary_unknown");
            }

            if (fullHash != null && !fullHash.isBlank()) {
                String tooltipText = timestampSeconds == null
                    ? fullHash
                    : fullHash + "\n" + TimeFormatter.formatDateTime(timestampSeconds * 1000L);
                lastSummaryLabel.setTooltip(new Tooltip(tooltipText));
            } else {
                lastSummaryLabel.setTooltip(null);
            }

            updateCountChip(aheadLabel, aheadDot, "railroad.git.branches.local.ahead", item.aheadCount());
            updateCountChip(behindLabel, behindDot, "railroad.git.branches.local.behind", item.behindCount());
            setStatus(statusLabel, item.status());
            updateStatusStyle(item.status());

            setText(null);
            setGraphic(root);
        }

        private static void setStatus(LocalizedLabel statusLabel, GitBranchStatus status) {
            if (status == null) {
                statusLabel.setKey("railroad.generic.unknown");
            } else {
                statusLabel.setKey(status.getTranslationKey());
            }
        }

        private void updateStatusStyle(GitBranchStatus status) {
            if (statusStyleClass != null) {
                statusLabel.getStyleClass().remove(statusStyleClass);
            }

            String newStatusStyleClass = "git-local-branch-status-unknown";
            if (status != null) {
                newStatusStyleClass = "git-local-branch-status-" + status.name().toLowerCase(Locale.ROOT);
            }

            if (!statusLabel.getStyleClass().contains(newStatusStyleClass)) {
                statusLabel.getStyleClass().add(newStatusStyleClass);
            }

            statusStyleClass = newStatusStyleClass;
        }

        private static void updateCountChip(LocalizedLabel label, Circle dot, String key, int count) {
            label.setKey(key, count);
            dot.getStyleClass().removeAll("git-local-branch-chip-dot-zero", "git-local-branch-chip-dot-nonzero");
            dot.getStyleClass().add(count == 0 ? "git-local-branch-chip-dot-zero" : "git-local-branch-chip-dot-nonzero");
        }
    }
}
