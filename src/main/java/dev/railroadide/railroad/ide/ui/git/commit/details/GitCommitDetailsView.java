package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import javafx.geometry.Pos;

import java.util.List;
import java.util.Map;

/**
 * Combines header, actions, metadata, and message cards for a commit.
 */
public class GitCommitDetailsView extends RRVBox {
    /**
     * Creates the complete details view for a commit and its references.
     *
     * @param project project whose files and workspace are being displayed
     * @param commit commit to display or act on
     * @param headCommitHash hash of the current HEAD commit
     * @param tagsByCommit tag names indexed by commit hash
     */
    public GitCommitDetailsView(
        Project project,
        GitCommit commit,
        String headCommitHash,
        Map<String, List<String>> tagsByCommit
    ) {
        super();
        getStyleClass().add("git-commit-details-view");
        setAlignment(Pos.TOP_LEFT);

        getChildren().add(new GitCommitHeaderCard(commit));
        getChildren().add(new GitCommitDetailsActionsBar(project, commit));
        getChildren().add(new GitCommitDetailsInfoCard(commit, headCommitHash, tagsByCommit));
        getChildren().add(new GitCommitDetailsMessageCard(project, commit));
    }
}
