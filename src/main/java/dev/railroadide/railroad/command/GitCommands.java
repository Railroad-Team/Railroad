package dev.railroadide.railroad.command;

import dev.railroadide.railroad.ide.ui.git.commit.GitCommitActionsPane;
import dev.railroadide.railroad.ide.ui.git.commit.GitCommitChangesPane;
import dev.railroadide.railroad.ide.ui.git.commit.details.*;
import dev.railroadide.railroad.ide.ui.git.remote.GitRemoteActionsPane;
import dev.railroadide.railroad.ide.ui.git.stash.GitStashPane;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;

import java.util.List;

/**
 * Existing Git operations shared across repository panes and command invocations.
 */
public final class GitCommands {
    /**
     * Prevents instantiation of command definitions.
     */
    private GitCommands() {
    }

    /**
     * fetch operation on the target repository.
     */
    public static final Command<GitManager> FETCH = CommandRegistry.action(
        "railroad:git_fetch", "railroad.git.overview.header.fetch.button", GitManager.class,
        GitManager::isActive, GitManager::fetch);
    /**
     * pull operation on the target repository.
     */
    public static final Command<GitManager> PULL = CommandRegistry.action(
        "railroad:git_pull", "railroad.git.overview.header.pull.button", GitManager.class,
        GitManager::isActive, GitManager::pull);
    /**
     * push operation on the target repository.
     */
    public static final Command<GitManager> PUSH = CommandRegistry.action(
        "railroad:git_push", "railroad.git.overview.header.push.button", GitManager.class,
        GitManager::isActive, GitManager::push);
    /**
     * fetch all operation on the target repository.
     */
    public static final Command<GitManager> FETCH_ALL = CommandRegistry.action(
        "railroad:git_fetch_all", "railroad.git.remotes.actions.button.fetch_all", GitManager.class,
        GitManager::isActive, GitManager::fetchAllRemotes);
    /**
     * prune all operation on the target repository.
     */
    public static final Command<GitManager> PRUNE_ALL = CommandRegistry.action(
        "railroad:git_prune_all", "railroad.git.remotes.actions.button.prune_all", GitManager.class,
        GitManager::isActive, GitManager::pruneAllRemotes);
    /**
     * gc operation on the target repository.
     */
    public static final Command<GitManager> GC = CommandRegistry.action(
        "railroad:git_gc", "railroad.git.remotes.actions.button.prune", GitManager.class,
        GitManager::isActive, GitManager::gc);
    /**
     * refresh operation on the target repository.
     */
    public static final Command<GitManager> REFRESH = CommandRegistry.action(
        "railroad:git_refresh", "git.commit.header.refresh.tooltip", GitManager.class,
        GitManager::isActive, GitManager::refreshStatus);
    /**
     * Opens the existing checkout commit workflow for the target commit.
     */
    public static final Command<GitCommit> CHECKOUT = CommandRegistry.register(new Command<>(
        "railroad:git_checkout_commit", "railroad.git.commit.details.button.checkout_commit",
        c -> c.project() != null && c.project().getGitManager().isActive() && c.argument() != null,
        c -> GitCommitCheckoutButton.execute(c.project(), c.argument()), List.of(), GitCommit.class));
    /**
     * Opens the existing cherry pick workflow for the target commit.
     */
    public static final Command<GitCommit> CHERRY_PICK = CommandRegistry.register(new Command<>(
        "railroad:git_cherry_pick", "railroad.git.commit.details.button.cherry_pick",
        c -> c.project() != null && c.project().getGitManager().isActive() && c.argument() != null,
        c -> GitCommitCherryPickButton.execute(c.project(), c.argument()), List.of(), GitCommit.class));
    /**
     * Opens the existing revert commit workflow for the target commit.
     */
    public static final Command<GitCommit> REVERT = CommandRegistry.register(new Command<>(
        "railroad:git_revert_commit", "railroad.git.commit.details.button.revert_commit",
        c -> c.project() != null && c.project().getGitManager().isActive() && c.argument() != null,
        c -> GitCommitRevertButton.execute(c.project(), c.argument()), List.of(), GitCommit.class));
    /**
     * Opens the existing create tag workflow for the target commit.
     */
    public static final Command<GitCommit> CREATE_TAG = CommandRegistry.register(new Command<>(
        "railroad:git_create_tag", "railroad.git.commit.details.button.create_tag",
        c -> c.project() != null && c.project().getGitManager().isActive() && c.argument() != null,
        c -> GitCommitCreateTagButton.execute(c.project(), c.argument()), List.of(), GitCommit.class));
    /**
     * Opens the existing create branch workflow for the target commit.
     */
    public static final Command<GitCommit> CREATE_BRANCH = CommandRegistry.register(new Command<>(
        "railroad:git_create_branch", "railroad.git.commit.details.button.create_branch",
        c -> c.project() != null && c.project().getGitManager().isActive() && c.argument() != null,
        c -> GitCommitNewBranchButton.execute(c.project(), c.argument()), List.of(), GitCommit.class));
    /**
     * Copies a commit's complete hash.
     */
    public static final Command<GitCommit> COPY_HASH = CommandRegistry.action(
        "railroad:git_copy_hash", "railroad.git.commit.details.button.copy_hash", GitCommit.class,
        _ -> true, GitCommitCopyHashButton::execute);
    /**
     * Commits selected changes with the current message and options.
     */
    public static final Command<GitCommitActionsPane> COMMIT = CommandRegistry.action(
        "railroad:git_commit", "git.commit.actions.commit.button", GitCommitActionsPane.class,
        _ -> true, p -> p.commitChanges(false));
    /**
     * Commits selected changes and requests a push.
     */
    public static final Command<GitCommitActionsPane> COMMIT_AND_PUSH = CommandRegistry.action(
        "railroad:git_commit_and_push", "git.commit.actions.commit_and_push.button", GitCommitActionsPane.class,
        _ -> true, p -> p.commitChanges(true));
    /**
     * Expands the pending-changes tree.
     */
    public static final Command<GitCommitChangesPane> EXPAND_CHANGES = CommandRegistry.action(
        "railroad:git_expand_changes", "git.commit.header.expand_all.tooltip", GitCommitChangesPane.class,
        _ -> true, GitCommitChangesPane::expandAll);
    /**
     * Collapses the pending-changes tree.
     */
    public static final Command<GitCommitChangesPane> COLLAPSE_CHANGES = CommandRegistry.action(
        "railroad:git_collapse_changes", "git.commit.header.collapse_all.tooltip", GitCommitChangesPane.class,
        _ -> true, GitCommitChangesPane::collapseAll);
    /**
     * create action for the stash pane's current state.
     */
    public static final Command<GitStashPane> STASH_CREATE = CommandRegistry.action(
        "railroad:git_stash_create", "railroad.git.stash.actions.create", GitStashPane.class,
        _ -> true, GitStashPane::onCreateStash);
    /**
     * refresh action for the stash pane's current state.
     */
    public static final Command<GitStashPane> STASH_REFRESH = CommandRegistry.action(
        "railroad:git_stash_refresh", "railroad.git.stash.actions.refresh", GitStashPane.class,
        GitStashPane::canRefreshStashes, GitStashPane::refreshStashes);
    /**
     * apply action for the stash pane's current state.
     */
    public static final Command<GitStashPane> STASH_APPLY = CommandRegistry.action(
        "railroad:git_stash_apply", "railroad.git.stash.actions.apply", GitStashPane.class,
        GitStashPane::hasSelectedStash, GitStashPane::onApplyStash);
    /**
     * pop action for the stash pane's current state.
     */
    public static final Command<GitStashPane> STASH_POP = CommandRegistry.action(
        "railroad:git_stash_pop", "railroad.git.stash.actions.pop", GitStashPane.class,
        GitStashPane::hasSelectedStash, GitStashPane::onPopStash);
    /**
     * drop action for the stash pane's current state.
     */
    public static final Command<GitStashPane> STASH_DROP = CommandRegistry.action(
        "railroad:git_stash_drop", "railroad.git.stash.actions.drop", GitStashPane.class,
        GitStashPane::hasSelectedStash, GitStashPane::onDropStash);
    /**
     * add action for the remote pane's current target.
     */
    public static final Command<GitRemoteActionsPane> REMOTE_ADD = CommandRegistry.action(
        "railroad:git_remote_add", "railroad.git.remotes.actions.button.add_remote",
        GitRemoteActionsPane.class, _ -> true, GitRemoteActionsPane::openAddRemoteDialog);
    /**
     * edit action for the remote pane's current target.
     */
    public static final Command<GitRemoteActionsPane> REMOTE_EDIT = CommandRegistry.action(
        "railroad:git_remote_edit", "railroad.git.remotes.actions.button.edit_remote",
        GitRemoteActionsPane.class, GitRemoteActionsPane::hasSelectedRemote, GitRemoteActionsPane::editSelectedRemote);
    /**
     * remove action for the remote pane's current target.
     */
    public static final Command<GitRemoteActionsPane> REMOTE_REMOVE = CommandRegistry.action(
        "railroad:git_remote_remove", "railroad.git.remotes.actions.button.remove_remote",
        GitRemoteActionsPane.class, GitRemoteActionsPane::hasSelectedRemote,
        GitRemoteActionsPane::removeSelectedRemote);
    /**
     * open browser action for the remote pane's current target.
     */
    public static final Command<GitRemoteActionsPane> REMOTE_OPEN_BROWSER = CommandRegistry.action(
        "railroad:git_remote_open_browser", "railroad.git.remotes.actions.button.open_in_browser",
        GitRemoteActionsPane.class, GitRemoteActionsPane::hasSelectedRemote,
        GitRemoteActionsPane::openSelectedRemoteInBrowser);

    /**
     * Opens the existing checkout branch workflow.
     */
    public static final Command<BranchTarget> BRANCH_CHECKOUT = CommandRegistry.action(
        "railroad:git_branch_checkout", "railroad.git.branches.actions.checkout", BranchTarget.class,
        t -> t.pane() != null && t.pane().canExecuteBranchAction("checkout", t.branch()),
        t -> t.pane().executeBranchAction("checkout", t.branch()));
    /**
     * Opens the existing set upstream branch workflow.
     */
    public static final Command<BranchTarget> BRANCH_SET_UPSTREAM = CommandRegistry.action(
        "railroad:git_branch_set_upstream", "railroad.git.branches.actions.set_upstream", BranchTarget.class,
        t -> t.pane() != null && t.pane().canExecuteBranchAction("set_upstream", t.branch()),
        t -> t.pane().executeBranchAction("set_upstream", t.branch()));
    /**
     * Opens the existing unset upstream branch workflow.
     */
    public static final Command<BranchTarget> BRANCH_UNSET_UPSTREAM = CommandRegistry.action(
        "railroad:git_branch_unset_upstream", "railroad.git.branches.actions.unset_upstream", BranchTarget.class,
        t -> t.pane() != null && t.pane().canExecuteBranchAction("unset_upstream", t.branch()),
        t -> t.pane().executeBranchAction("unset_upstream", t.branch()));
    /**
     * Opens the existing rename branch workflow.
     */
    public static final Command<BranchTarget> BRANCH_RENAME = CommandRegistry.action(
        "railroad:git_branch_rename", "railroad.git.branches.actions.rename", BranchTarget.class,
        t -> t.pane() != null && t.pane().canExecuteBranchAction("rename", t.branch()),
        t -> t.pane().executeBranchAction("rename", t.branch()));
    /**
     * Opens the existing delete branch workflow.
     */
    public static final Command<BranchTarget> BRANCH_DELETE = CommandRegistry.action(
        "railroad:git_branch_delete", "railroad.git.branches.actions.delete", BranchTarget.class,
        t -> t.pane() != null && t.pane().canExecuteBranchAction("delete", t.branch()),
        t -> t.pane().executeBranchAction("delete", t.branch()));

    /**
     * Initializes the Git command definitions.
     */
    public static void initialize() {
    }
}
