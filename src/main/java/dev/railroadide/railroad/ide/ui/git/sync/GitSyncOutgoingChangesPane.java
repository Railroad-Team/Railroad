package dev.railroadide.railroad.ide.ui.git.sync;

import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.railroad.vcs.git.GitManager;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class GitSyncOutgoingChangesPane extends RRVBox {
    public GitSyncOutgoingChangesPane(GitManager gitManager) {
        getStyleClass().add("git-sync-outgoing-changes-pane");

        var title = new LocalizedText("railroad.git.sync.outgoing_changes.title");
        title.getStyleClass().add("git-sync-outgoing-changes-title");

        var commitsList = new GitSyncCommitsList();
        commitsList.setNoCommitsText("railroad.git.sync.outgoing_changes.no_commits");
        commitsList.setCommits(gitManager.getOutgoingCommits());

        getChildren().addAll(title, commitsList);
        VBox.setVgrow(commitsList, Priority.ALWAYS);
    }
}
