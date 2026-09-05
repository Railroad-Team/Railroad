package dev.railroadide.railroad.ide.ui.git.sync;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.vcs.git.GitManager;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Combines synchronization status, controls, and incoming and outgoing commit lists.
 */
public class GitSyncPane extends RRVBox {
    private final GitSyncInfoPane infoPane;
    private final GitSyncControlsPane controlsPane;
    private final GitSyncIncomingChangesPane incomingChangesPane;
    private final GitSyncOutgoingChangesPane outgoingChangesPane;

    /**
     * Creates synchronization information, controls, and commit lists for a project.
     *
     * @param project project whose files and workspace are being displayed
     */
    public GitSyncPane(Project project) {
        Services.UI_MANAGER.assignWhileAttached(UIIds.Git.GIT_SYNC, this);
        getStyleClass().add("git-sync-pane-root");

        GitManager gitManager = project.getGitManager();
        this.infoPane = new GitSyncInfoPane(gitManager);
        this.controlsPane = new GitSyncControlsPane(gitManager);
        this.incomingChangesPane = new GitSyncIncomingChangesPane(gitManager);
        this.outgoingChangesPane = new GitSyncOutgoingChangesPane(gitManager);

        getChildren().addAll(
            infoPane,
            controlsPane,
            incomingChangesPane,
            outgoingChangesPane);

        VBox.setVgrow(incomingChangesPane, Priority.ALWAYS);
        VBox.setVgrow(outgoingChangesPane, Priority.ALWAYS);
    }
}
