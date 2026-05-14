package dev.railroadide.railroad.ide.ui.git.commit.list;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRVBox;
import javafx.scene.layout.Priority;

public class GitCommitListPane extends RRVBox {
    private final GitCommitListHeaderPane header;
    private final GitCommitListViewPane commitListView;

    public GitCommitListPane(Project project) {
        super();
        getStyleClass().add("git-commit-list-pane");

        this.commitListView = new GitCommitListViewPane(project);
        this.commitListView.getStyleClass().add("git-commit-list-view");

        this.header = new GitCommitListHeaderPane(project.getGitManager(), this.commitListView);
        this.header.getStyleClass().add("git-commit-list-header-pane");

        getChildren().addAll(this.header, this.commitListView);
        RRVBox.setVgrow(this.commitListView, Priority.ALWAYS);
    }
}
