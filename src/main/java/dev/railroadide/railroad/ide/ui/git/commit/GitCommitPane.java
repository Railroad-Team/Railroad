package dev.railroadide.railroad.ide.ui.git.commit;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRVBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class GitCommitPane extends RRVBox {
    public GitCommitPane(Project project) {
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
    }
}
