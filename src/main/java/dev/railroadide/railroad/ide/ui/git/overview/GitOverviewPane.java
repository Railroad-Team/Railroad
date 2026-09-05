package dev.railroadide.railroad.ide.ui.git.overview;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Combines repository actions, identity information, and recent commits.
 */
public class GitOverviewPane extends RRVBox {
    private final GitOverviewHeaderPane headerPane;
    private final GitOverviewIdentityPane identityPane;
    private final GitOverviewRecentCommitsPane recentCommitsPane;

    /**
     * Creates the repository overview sections for a project.
     *
     * @param project project whose files and workspace are being displayed
     */
    public GitOverviewPane(Project project) {
        Services.UI_MANAGER.assignWhileAttached(UIIds.Git.GIT_OVERVIEW, this);
        getStyleClass().add("git-overview-pane-root");

        this.headerPane = new GitOverviewHeaderPane(project);
        this.identityPane = new GitOverviewIdentityPane(project);
        this.recentCommitsPane = new GitOverviewRecentCommitsPane(project);

        getChildren().addAll(
            headerPane,
            identityPane,
            recentCommitsPane);

        VBox.setVgrow(recentCommitsPane, Priority.ALWAYS);
    }
}
