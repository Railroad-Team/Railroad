package dev.railroadide.railroad.ide.ui.git.remote;

import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.remote.GitUpstream;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Objects;

public class GitRemotesPane extends RRVBox {
    private final GitRemoteActionsPane actionsPane;
    private final GitRemotesListPane remotesList;
    private final GitRemoteDetailsPane detailsPane;

    public GitRemotesPane(Project project) {
        GitManager gitManager = project.getGitManager();

        actionsPane = new GitRemoteActionsPane(gitManager);
        remotesList = new GitRemotesListPane(gitManager);
        detailsPane = new GitRemoteDetailsPane(gitManager, gitManager.getRemotes()
            .stream()
            .filter(remote -> Objects.equals(
                remote.name(),
                gitManager.getUpstream().map(GitUpstream::remoteName).orElse(null)
            ))
            .findFirst()
            .orElse(null));

        remotesList.setOnRemoteSelected(detailsPane::displayRemote);
        remotesList.setOnRemoteSelected(actionsPane::updateActions);

        getChildren().addAll(actionsPane, remotesList, detailsPane);
        setSpacing(10);
        getStyleClass().add("git-remotes-pane");
        VBox.setVgrow(remotesList, Priority.ALWAYS);
    }
}
