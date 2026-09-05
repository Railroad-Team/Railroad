package dev.railroadide.railroad.ide.ui.git.sync;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import javafx.application.Platform;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Displays local commits awaiting synchronization with the tracked remote.
 */
public class GitSyncOutgoingChangesPane extends RRVBox {
    /**
     * Creates the outgoing commit list and subscribes to repository status changes.
     *
     * @param gitManager repository service supplying state and Git operations
     */
    public GitSyncOutgoingChangesPane(GitManager gitManager) {
        Services.UI_MANAGER.assignWhileAttached(UIIds.Git.GIT_SYNC_OUTGOING_CHANGES, this);
        getStyleClass().add("git-sync-outgoing-changes-pane");

        var title = new LocalizedText("railroad.git.sync.outgoing_changes.title");
        title.getStyleClass().add("git-sync-outgoing-changes-title");

        var commitsList = new GitSyncCommitsList();
        commitsList.setNoCommitsText("railroad.git.sync.outgoing_changes.no_commits");
        commitsList.setCommits(gitManager.getOutgoingCommits());
        gitManager.repoStatusProperty().addListener((_, _, _) -> {
            List<GitCommit> outgoingCommits = gitManager.getOutgoingCommits();
            Platform.runLater(() -> commitsList.setCommits(outgoingCommits));
        });

        getChildren().addAll(title, commitsList);
        VBox.setVgrow(commitsList, Priority.ALWAYS);
    }
}
