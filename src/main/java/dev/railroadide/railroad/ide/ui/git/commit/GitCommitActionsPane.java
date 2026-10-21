package dev.railroadide.railroad.ide.ui.git.commit;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.CommandButtons;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.GitCommands;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.*;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.vcs.git.commit.GitCommitData;

/**
 * Collects a commit message and options and submits selected changes to Git.
 */
public class GitCommitActionsPane extends RRVBox {
    private final Project project;
    private final GitCommitChangesPane gitCommitChanges;

    private final RRCheckBox amendCheckbox;
    private final RRCheckBox signOffCheckbox;
    private final RRTextField commitMessageField;
    private final RRTextArea commitDescriptionArea;

    /**
     * Creates commit message and option controls linked to a change-selection pane.
     *
     * @param project project whose files and workspace are being displayed
     * @param gitCommitChanges change-selection pane supplying files to commit
     */
    public GitCommitActionsPane(Project project, GitCommitChangesPane gitCommitChanges) {
        Services.UI_MANAGER.assignWhileAttached(UIIds.Git.GIT_COMMIT_ACTIONS, this);
        this.project = project;
        this.gitCommitChanges = gitCommitChanges;

        getStyleClass().add("git-commit-actions-pane");

        var header = new RRHBox();
        header.getStyleClass().add("git-commit-actions-header");

        this.amendCheckbox = new RRCheckBox("git.commit.actions.amend.checkbox");
        header.getChildren().add(this.amendCheckbox);

        this.signOffCheckbox = new RRCheckBox("git.commit.actions.signoff.checkbox");
        header.getChildren().add(this.signOffCheckbox);

        getChildren().add(header);

        this.commitMessageField = new RRTextField("git.commit.actions.message.placeholder");
        this.commitMessageField.getStyleClass().add("git-commit-message-field");
        getChildren().add(this.commitMessageField);

        this.commitDescriptionArea = new RRTextArea("git.commit.actions.description.placeholder");
        this.commitDescriptionArea.getStyleClass().add("git-commit-description-area");
        this.commitDescriptionArea.setWrapText(true);
        getChildren().add(this.commitDescriptionArea);

        var footer = new RRHBox();
        footer.getStyleClass().add("git-commit-actions-footer");

        var commitButton = RRButton.primary("git.commit.actions.commit.button");
        var commitAndPushButton = RRButton.primary("git.commit.actions.commit_and_push.button");

        footer.getChildren().addAll(commitButton, commitAndPushButton);
        getChildren().add(footer);

        CommandButtons.bind(commitButton, GitCommands.COMMIT,
            () -> CommandContext.withArgument(project, this, this));
        CommandButtons.bind(commitAndPushButton, GitCommands.COMMIT_AND_PUSH,
            () -> CommandContext.withArgument(project, this, this));
    }

    /**
     * Clears the commit message and description and resets amend and sign-off options.
     */
    public void clearCommitFields() {
        this.commitMessageField.clear();
        this.commitDescriptionArea.clear();
        this.amendCheckbox.setSelected(false);
        this.signOffCheckbox.setSelected(false);
    }

    /**
     * Submits the selected changes and entered commit options, refreshes status, and clears the fields.
     *
     * @param pushAfterCommit whether Git should push after creating the commit
     */
    public void commitChanges(boolean pushAfterCommit) {
        String message = this.commitMessageField.getText();
        String description = this.commitDescriptionArea.getText();
        boolean isAmend = this.amendCheckbox.isSelected();
        boolean isSignOff = this.signOffCheckbox.isSelected();

        var commit = new GitCommitData(message, description, isAmend, isSignOff, gitCommitChanges.getSelectedChanges());
        project.getGitManager().commitChanges(commit, pushAfterCommit);
        project.getGitManager().refreshStatus();
        clearCommitFields();
    }
}
