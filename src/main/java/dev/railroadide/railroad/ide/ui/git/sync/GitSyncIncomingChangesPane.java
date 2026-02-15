package dev.railroadide.railroad.ide.ui.git.sync;

import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import javafx.application.Platform;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

public class GitSyncIncomingChangesPane extends RRVBox {
    public GitSyncIncomingChangesPane(GitManager gitManager) {
        getStyleClass().add("git-sync-incoming-changes-pane");

        var title = new LocalizedText("railroad.git.sync.incoming_changes.title");
        title.getStyleClass().add("git-sync-incoming-changes-title");

        var commitsList = new GitSyncCommitsList();
        commitsList.setNoCommitsText("railroad.git.sync.incoming_changes.no_commits");
        commitsList.setCommits(gitManager.getIncomingCommits());
        gitManager.repoStatusProperty().addListener((observable, oldValue, newValue) -> {
            List<GitCommit> incomingCommits = gitManager.getIncomingCommits();
            Platform.runLater(() -> commitsList.setCommits(incomingCommits));
        });

        getChildren().addAll(title, commitsList);
        VBox.setVgrow(commitsList, Priority.ALWAYS);
    }
}
