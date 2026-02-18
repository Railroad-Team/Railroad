package dev.railroadide.railroad.ide.ui.git.overview;

import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.ui.RRVBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class GitOverviewPane extends RRVBox {
    private final GitOverviewHeaderPane headerPane;
    private final GitOverviewIdentityPane identityPane;
    private final GitOverviewRecentCommitsPane recentCommitsPane;

    public GitOverviewPane(RailroadProject project) {
        getStyleClass().add("git-overview-pane-root");
        setSpacing(8);

        this.headerPane = new GitOverviewHeaderPane(project);
        this.identityPane = new GitOverviewIdentityPane(project);
        this.recentCommitsPane = new GitOverviewRecentCommitsPane(project);

        getChildren().addAll(
            headerPane,
            identityPane,
            recentCommitsPane
        );

        VBox.setVgrow(recentCommitsPane, Priority.ALWAYS);
    }
}
