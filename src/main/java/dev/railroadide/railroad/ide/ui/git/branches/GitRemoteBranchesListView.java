package dev.railroadide.railroad.ide.ui.git.branches;

import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.vcs.git.branch.GitBranch;
import dev.railroadide.railroad.vcs.git.branch.GitBranchLastCommit;
import dev.railroadide.railroad.vcs.git.branch.GitBranchStatus;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Locale;

public class GitRemoteBranchesListView extends AbstractGitBranchesListView<GitBranch.RemoteGitBranch> {
    public GitRemoteBranchesListView(RailroadProject project) {
        super(
            project,
            "git-remote-branches-list-view",
            value -> value.getGitManager().getAllRemoteBranches(),
            listView -> new GitRemoteBranchCell()
        );
    }

    @Override
    Node createDetailsNode(GitBranch.RemoteGitBranch branch) {
        var rows = new ArrayList<Node>();
        rows.add(createTextDetailsRow("railroad.git.branches.details.remote", branch.remoteName()));
        return createCommonDetailsNode(branch, rows);
    }

    private static class GitRemoteBranchCell extends ListCell<GitBranch.RemoteGitBranch> {
        private final HBox root = new RRHBox(8);
        private final VBox details = new RRVBox(2);
        private final HBox titleRow = new RRHBox(6);
        private final HBox metadataRow = new RRHBox(6);

        private final Label branchNameLabel = new Label();
        private final Label remoteNameLabel = new Label();
        private final LocalizedLabel statusLabel = new LocalizedLabel("");
        private final LocalizedLabel lastSummaryLabel = new LocalizedLabel("");

        private String statusStyleClass;

        private GitRemoteBranchCell() {
            getStyleClass().add("git-remote-branch-list-cell");

            root.getStyleClass().add("git-remote-branch-cell");
            details.getStyleClass().add("git-remote-branch-details");
            titleRow.getStyleClass().add("git-remote-branch-title-row");
            metadataRow.getStyleClass().add("git-remote-branch-metadata-row");
            root.setAlignment(Pos.CENTER_LEFT);

            branchNameLabel.getStyleClass().add("git-remote-branch-name");
            remoteNameLabel.getStyleClass().add("git-remote-branch-remote-badge");
            statusLabel.getStyleClass().add("git-remote-branch-status");
            lastSummaryLabel.getStyleClass().add("git-remote-branch-last-summary");

            HBox.setHgrow(details, Priority.ALWAYS);
            HBox.setHgrow(lastSummaryLabel, Priority.ALWAYS);

            titleRow.getChildren().addAll(branchNameLabel, remoteNameLabel);
            metadataRow.getChildren().add(lastSummaryLabel);
            details.getChildren().addAll(titleRow, metadataRow);
            root.getChildren().addAll(details, statusLabel);
        }

        @Override
        protected void updateItem(GitBranch.RemoteGitBranch item, boolean empty) {
            super.updateItem(item, empty);

            if (empty || item == null) {
                branchNameLabel.setText(null);
                remoteNameLabel.setText(null);
                statusLabel.setText(null);
                lastSummaryLabel.setText(null);
                lastSummaryLabel.setTooltip(null);
                setText(null);
                setGraphic(null);
                return;
            }

            branchNameLabel.setText(item.name());
            remoteNameLabel.setText(item.remoteName());

            setStatus(statusLabel, item.status());
            updateStatusStyle(item.status());

            GitBranchLastCommit lastCommit = item.lastCommit();
            String fullHash = lastCommit == null ? null : lastCommit.hash();
            if (fullHash != null && !fullHash.isBlank()) {
                String shortHash = fullHash.length() > 8 ? fullHash.substring(0, 8) : fullHash;
                lastSummaryLabel.setKey("railroad.git.branches.remote.last_summary", shortHash);
                lastSummaryLabel.setTooltip(new Tooltip(fullHash));
            } else {
                lastSummaryLabel.setKey("railroad.git.branches.remote.last_summary_unknown");
                lastSummaryLabel.setTooltip(null);
            }

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

            String newStatusStyleClass = "git-remote-branch-status-unknown";
            if (status != null) {
                newStatusStyleClass = "git-remote-branch-status-" + status.name().toLowerCase(Locale.ROOT);
            }

            if (!statusLabel.getStyleClass().contains(newStatusStyleClass)) {
                statusLabel.getStyleClass().add(newStatusStyleClass);
            }

            statusStyleClass = newStatusStyleClass;
        }
    }
}
