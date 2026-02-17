package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.core.ui.*;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.core.ui.styling.ButtonVariant;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

public class GitCommitCreateTagButton extends RRButton {
    public GitCommitCreateTagButton(Project project, GitCommit commit) {
        super("railroad.git.commit.details.button.create_tag", FontAwesomeSolid.TAG);
        setVariant(ButtonVariant.PRIMARY);
        setOnAction(event -> {
            var tagVbox = new RRVBox(10);
            tagVbox.getStyleClass().add("git-commit-new-tag-dialog-content");

            var tagNameLabel = new LocalizedText("railroad.git.commit.details.new_tag_dialog.tag_name_label");
            tagNameLabel.getStyleClass().add("git-commit-new-tag-name-label");
            tagVbox.getChildren().add(tagNameLabel);

            var tagNameField = new RRTextField("railroad.git.commit.details.new_tag_dialog.tag_name_placeholder");
            tagNameField.getStyleClass().add("git-commit-new-tag-name-text-field");
            tagVbox.getChildren().add(tagNameField);

            var errorText = new LocalizedText("");
            errorText.getStyleClass().add("git-commit-new-tag-error-text");
            tagVbox.getChildren().add(errorText);

            var overwriteCheckbox = new RRCheckBox("railroad.git.commit.details.new_tag_dialog.overwrite_tag_checkbox");
            overwriteCheckbox.getStyleClass().add("git-commit-new-tag-overwrite-checkbox");
            overwriteCheckbox.setSelected(false);
            tagVbox.getChildren().add(overwriteCheckbox);

            var messageVbox = new RRVBox(5);
            messageVbox.getStyleClass().add("git-commit-new-tag-message-vbox");
            var messageText = new LocalizedText("railroad.git.commit.details.new_tag_dialog.annotation_message");
            messageText.getStyleClass().add("git-commit-new-tag-message-text");
            messageVbox.getChildren().add(messageText);

            var messageArea = new RRTextArea("railroad.git.commit.details.new_tag_dialog.annotation_message_placeholder");
            messageArea.getStyleClass().add("git-commit-new-tag-message-area");
            messageArea.setWrapText(true);
            messageVbox.getChildren().add(messageArea);
            tagVbox.getChildren().add(messageVbox);

            DialogBuilder dialogBuilder = DialogBuilder.create()
                .title("railroad.git.commit.details.new_tag_dialog.title")
                .contentNode(tagVbox)
                .onConfirm(() -> {
                    String tagName = tagNameField.getText().trim();
                    if (!tagName.isBlank() && project.getGitManager().isValidTagName(tagName)) {
                        project.getGitManager().createTag(tagName, commit.hash(), messageArea.getText().strip(), overwriteCheckbox.isSelected());
                    }
                });
            var dialog = WindowBuilder.createDialog("railroad.git.commit.details.new_tag_dialog.title", dialogBuilder);

            var confirmButton = (RRButton) dialog.getScene().lookup(".rr-button.success");
            if (confirmButton != null) {
                confirmButton.setDisable(true);
            }

            tagNameField.textProperty().addListener((obs, oldText, newText) -> {
                if (newText.strip().isBlank()) {
                    errorText.setKeyAndArgs("");
                    if (confirmButton != null) {
                        confirmButton.setDisable(true);
                    }
                } else {
                    if (newText.contains(" ")) {
                        errorText.setKeyAndArgs("railroad.git.commit.details.new_tag_dialog.error_no_spaces");
                        if (confirmButton != null) {
                            confirmButton.setDisable(true);
                        }
                        return;
                    }

                    if (!overwriteCheckbox.isSelected() && project.getGitManager().doesTagExist(newText)) {
                        errorText.setKeyAndArgs("railroad.git.commit.details.new_tag_dialog.error_tag_exists");
                        if (confirmButton != null) {
                            confirmButton.setDisable(true);
                        }
                        return;
                    }

                    if (!project.getGitManager().isValidTagName(newText)) {
                        errorText.setKeyAndArgs("railroad.git.commit.details.new_tag_dialog.error_invalid_tag_name");
                        if (confirmButton != null) {
                            confirmButton.setDisable(true);
                        }
                        return;
                    }

                    errorText.setKeyAndArgs("");
                    if (confirmButton != null) {
                        confirmButton.setDisable(false);
                    }
                }
            });

        });
    }
}
