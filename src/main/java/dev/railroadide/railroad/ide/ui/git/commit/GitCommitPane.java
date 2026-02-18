package dev.railroadide.railroad.ide.ui.git.commit;

import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.ui.RRVBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.Getter;

public class GitCommitPane extends RRVBox {
    @Getter
    private final RailroadProject project;

    public GitCommitPane(RailroadProject project) {
        this.project = project;

        getStyleClass().add("git-commit-pane-root");

        var gitCommitChanges = new GitCommitChangesPane(project);
        var gitCommitHeader = new GitCommitHeaderPane(project, gitCommitChanges);
        var gitCommitActions = new GitCommitActionsPane(project, gitCommitChanges);

        getChildren().addAll(
            gitCommitHeader,
            gitCommitChanges,
            gitCommitActions
        );

        VBox.setVgrow(gitCommitChanges, Priority.ALWAYS);
        gitCommitChanges.setMaxHeight(Double.MAX_VALUE);
        gitCommitChanges.setMinHeight(0);
    }
}
