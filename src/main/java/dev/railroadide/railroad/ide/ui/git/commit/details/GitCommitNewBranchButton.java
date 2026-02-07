package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRCheckBox;
import dev.railroadide.core.ui.RRTextField;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.core.ui.styling.ButtonVariant;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

public class GitCommitNewBranchButton extends RRButton {
    public GitCommitNewBranchButton(Project project, GitCommit commit) {
        super("railroad.git.commit.details.button.create_branch", FontAwesomeSolid.CODE_BRANCH);
        setVariant(ButtonVariant.PRIMARY);
        setOnAction(event -> {
            var branchVbox = new RRVBox(10);
            branchVbox.getStyleClass().add("git-commit-new-branch-dialog-content");

            var branchNameLabel = new LocalizedText("railroad.git.commit.details.new_branch_dialog.branch_name_label");
            branchNameLabel.getStyleClass().add("git-commit-new-branch-name-label");
            branchVbox.getChildren().add(branchNameLabel);

            var branchNameField = new RRTextField("railroad.git.commit.details.new_branch_dialog.branch_name_placeholder");
            branchNameField.getStyleClass().add("git-commit-new-branch-name-text-field");
            branchVbox.getChildren().add(branchNameField);

            var errorText = new LocalizedText("");
            errorText.getStyleClass().add("git-commit-new-branch-error-text");
            branchVbox.getChildren().add(errorText);

            var checkoutCheckbox = new RRCheckBox("railroad.git.commit.details.new_branch_dialog.checkout_branch_checkbox");
            checkoutCheckbox.getStyleClass().add("git-commit-new-branch-checkout-checkbox");
            checkoutCheckbox.setSelected(true);
            branchVbox.getChildren().add(checkoutCheckbox);

            DialogBuilder dialogBuilder = DialogBuilder.create()
                .title("railroad.git.commit.details.new_branch_dialog.title")
                .contentNode(branchVbox)
                .onConfirm(() -> {
                    String branchName = branchNameField.getText().trim();
                    if (!branchName.isEmpty() && project.getGitManager().isValidBranchName(branchName)) {
                        project.getGitManager().createBranch(branchName, commit.hash(), checkoutCheckbox.isSelected());
                    }
                });

            Stage dialog = WindowBuilder.createDialog("railroad.git.commit.details.new_branch_dialog.title", dialogBuilder);
            var confirmButton = (RRButton) dialogBuilder.buildScene().lookup(".rr-button.success");
            confirmButton.setDisable(true);
            branchNameField.textProperty().addListener((obs, oldText, newText) -> {
                if (newText.strip().isBlank()) {
                    errorText.setKeyAndArgs("");
                    confirmButton.setDisable(true);
                } else {
                    validateBranchName(project, newText, errorText, confirmButton);
                }
            });
            dialog.showAndWait();
        });
    }

    private static void validateBranchName(Project project, String string, LocalizedText errorText, RRButton confirmButton) {
        boolean hasControlChars = string.chars().anyMatch(c -> c < 32 || c == 127);
        if (hasControlChars) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_invalid_characters");
            confirmButton.setDisable(true);
            return;
        }

        if (string.contains(" ")) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_no_spaces");
            confirmButton.setDisable(true);
            return;
        }

        if (string.endsWith(".") || string.endsWith("/")) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_invalid_ending");
            confirmButton.setDisable(true);
            return;
        }

        if (string.contains("..")) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_double_dots");
            confirmButton.setDisable(true);
            return;
        }

        if (string.contains("//") || string.contains("@{") || string.contains("\\")) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_invalid_sequences");
            confirmButton.setDisable(true);
            return;
        }

        if (string.equals("HEAD")) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_head_reserved");
            confirmButton.setDisable(true);
            return;
        }

        if (!project.getGitManager().isValidBranchName(string)) {
            errorText.setKeyAndArgs("railroad.git.commit.details.new_branch_dialog.error_invalid_branch_name");
            confirmButton.setDisable(true);
            return;
        }

        errorText.setKeyAndArgs("");
        confirmButton.setDisable(false);
    }
}
