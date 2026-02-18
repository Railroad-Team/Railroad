package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import javafx.geometry.Pos;

public class GitCommitDetailsActionsBar extends RRHBox {
    public GitCommitDetailsActionsBar(RailroadProject project, GitCommit commit) {
        super(5);
        getStyleClass().add("git-commit-details-buttons-hbox");
        setAlignment(Pos.CENTER_LEFT);

        getChildren().add(new GitCommitCopyHashButton(commit));
        getChildren().add(new GitCommitCheckoutButton(project, commit));
        getChildren().add(new GitCommitNewBranchButton(project, commit));
        getChildren().add(new GitCommitCreateTagButton(project, commit));
        getChildren().add(new GitCommitCherryPickButton(project, commit));
        getChildren().add(new GitCommitRevertButton(project, commit));
    }
}
