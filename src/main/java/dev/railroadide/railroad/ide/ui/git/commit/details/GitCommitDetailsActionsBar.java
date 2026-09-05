package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import javafx.geometry.Pos;

/**
 * Groups copy, checkout, branch, tag, cherry-pick, and revert actions for a commit.
 */
public class GitCommitDetailsActionsBar extends RRHBox {
    /**
     * Creates the repository action buttons for the supplied commit.
     *
     * @param project project whose files and workspace are being displayed
     * @param commit commit to display or act on
     */
    public GitCommitDetailsActionsBar(Project project, GitCommit commit) {
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
