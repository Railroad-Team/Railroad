package dev.railroadide.railroad.ide.ui.git.overview;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRVBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class GitOverviewPane extends RRVBox {
    private final GitOverviewHeaderPane headerPane;
    private final GitOverviewIdentityPane identityPane;
    private final GitOverviewRecentCommitsPane recentCommitsPane;

    public GitOverviewPane(Project project) {
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
