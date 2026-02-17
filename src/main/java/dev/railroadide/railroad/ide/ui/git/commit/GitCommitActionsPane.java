package dev.railroadide.railroad.ide.ui.git.commit;

import dev.railroadide.core.ui.*;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.vcs.git.commit.GitCommitData;

public class GitCommitActionsPane extends RRVBox {
    private final Project project;
    private final GitCommitChangesPane gitCommitChanges;

    private final RRCheckBox amendCheckbox;
    private final RRCheckBox signOffCheckbox;
    private final RRTextField commitMessageField;
    private final RRTextArea commitDescriptionArea;

    public GitCommitActionsPane(Project project, GitCommitChangesPane gitCommitChanges) {
        this.project = project;
        this.gitCommitChanges = gitCommitChanges;

        getStyleClass().add("git-commit-actions-pane");
        setSpacing(8);

        var header = new RRHBox(4);
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

        var footer = new RRHBox(4);
        footer.getStyleClass().add("git-commit-actions-footer");

        var commitButton = RRButton.primary("git.commit.actions.commit.button");
        var commitAndPushButton = RRButton.primary("git.commit.actions.commit_and_push.button");

        footer.getChildren().addAll(commitButton, commitAndPushButton);
        getChildren().add(footer);

        commitButton.setOnAction(event -> commitChanges(false));
        commitAndPushButton.setOnAction(event -> commitChanges(true));
    }

    public void clearCommitFields() {
        this.commitMessageField.clear();
        this.commitDescriptionArea.clear();
        this.amendCheckbox.setSelected(false);
        this.signOffCheckbox.setSelected(false);
    }

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
