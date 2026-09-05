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
 * Displays commits available from the tracked remote but not yet incorporated locally.
 */
public class GitSyncIncomingChangesPane extends RRVBox {
    /**
     * Creates the incoming commit list and subscribes to repository status changes.
     *
     * @param gitManager repository service supplying state and Git operations
     */
    public GitSyncIncomingChangesPane(GitManager gitManager) {
        Services.UI_MANAGER.assignWhileAttached(UIIds.Git.GIT_SYNC_INCOMING_CHANGES, this);
        getStyleClass().add("git-sync-incoming-changes-pane");

        var title = new LocalizedText("railroad.git.sync.incoming_changes.title");
        title.getStyleClass().add("git-sync-incoming-changes-title");

        var commitsList = new GitSyncCommitsList();
        commitsList.setNoCommitsText("railroad.git.sync.incoming_changes.no_commits");
        commitsList.setCommits(gitManager.getIncomingCommits());
        gitManager.repoStatusProperty().addListener((_, _, _) -> {
            List<GitCommit> incomingCommits = gitManager.getIncomingCommits();
            Platform.runLater(() -> commitsList.setCommits(incomingCommits));
        });

        getChildren().addAll(title, commitsList);
        VBox.setVgrow(commitsList, Priority.ALWAYS);
    }
}
