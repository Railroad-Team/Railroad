package dev.railroadide.railroad.ide.ui.git.sync;

import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import javafx.application.Platform;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

public class GitSyncOutgoingChangesPane extends RRVBox {
    public GitSyncOutgoingChangesPane(GitManager gitManager) {
        getStyleClass().add("git-sync-outgoing-changes-pane");

        var title = new LocalizedText("railroad.git.sync.outgoing_changes.title");
        title.getStyleClass().add("git-sync-outgoing-changes-title");

        var commitsList = new GitSyncCommitsList();
        commitsList.setNoCommitsText("railroad.git.sync.outgoing_changes.no_commits");
        commitsList.setCommits(gitManager.getOutgoingCommits());
        gitManager.repoStatusProperty().addListener((observable, oldValue, newValue) -> {
            List<GitCommit> outgoingCommits = gitManager.getOutgoingCommits();
            Platform.runLater(() -> commitsList.setCommits(outgoingCommits));
        });

        getChildren().addAll(title, commitsList);
        VBox.setVgrow(commitsList, Priority.ALWAYS);
    }
}
