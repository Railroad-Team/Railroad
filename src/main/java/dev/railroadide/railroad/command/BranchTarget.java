package dev.railroadide.railroad.command;

import dev.railroadide.railroad.ide.ui.git.branches.AbstractGitBranchesListView;
import dev.railroadide.railroad.vcs.git.branch.GitBranch;

/**
 * A branch and the pane that owns its existing confirmation workflows.
 *
 * @param pane pane supplying the repository and dialogs
 * @param branch branch selected for the operation
 */
public record BranchTarget(AbstractGitBranchesListView<?> pane, GitBranch branch) {
}
