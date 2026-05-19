package dev.railroadide.railroad.ide.ui.git.remote;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.remote.GitRemote;
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
        detailsPane = new GitRemoteDetailsPane(gitManager, findDefaultRemote(gitManager));

        remotesList.setOnRemoteSelected(detailsPane::displayRemote);
        remotesList.setOnRemoteSelected(actionsPane::updateActions);
        gitManager.repoStatusProperty().addListener((obs, oldStatus, newStatus) -> {
            if (detailsPane.remoteProperty().get() == null) {
                GitRemote defaultRemote = findDefaultRemote(gitManager);
                if (defaultRemote != null) {
                    detailsPane.displayRemote(defaultRemote);
                    actionsPane.updateActions(defaultRemote);
                }
            }
        });

        getChildren().addAll(actionsPane, remotesList, detailsPane);
        getStyleClass().add("git-remotes-pane");
        VBox.setVgrow(remotesList, Priority.ALWAYS);
    }

    private static GitRemote findDefaultRemote(GitManager gitManager) {
        return gitManager.getRemotes()
            .stream()
            .filter(remote -> Objects.equals(
                remote.name(),
                gitManager.getUpstream().map(GitUpstream::remoteName).orElse(null)
            ))
            .findFirst()
            .orElse(null);
    }
}
