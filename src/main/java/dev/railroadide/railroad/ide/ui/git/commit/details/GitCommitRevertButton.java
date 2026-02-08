package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.core.ui.styling.ButtonVariant;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

public class GitCommitRevertButton extends RRButton {
    public GitCommitRevertButton(Project project, GitCommit commit) {
        super("railroad.git.commit.details.button.revert_commit", FontAwesomeSolid.UNDO);
        setVariant(ButtonVariant.DANGER);
        setOnAction(event -> {
            var content = new LocalizedText(
                "railroad.git.commit.details.revert_dialog.content",
                commit.shortHash()
            );
            content.getStyleClass().add("git-commit-revert-dialog-content");

            var cancelButton = new RRButton("railroad.generic.cancel");
            cancelButton.setVariant(ButtonVariant.SECONDARY);
            cancelButton.getStyleClass().add("git-commit-revert-dialog-cancel-button");

            var confirmButton = new RRButton("railroad.git.commit.details.revert_dialog.confirm");
            confirmButton.setVariant(ButtonVariant.DANGER);
            confirmButton.getStyleClass().add("git-commit-revert-dialog-confirm-button");

            DialogBuilder dialogBuilder = DialogBuilder.create()
                .title("railroad.git.commit.details.revert_dialog.subtitle")
                .contentNode(content)
                .buttons(cancelButton, confirmButton);
            Stage dialog = WindowBuilder.createDialog("railroad.git.commit.details.revert_dialog.title", dialogBuilder);

            cancelButton.setOnAction($ -> dialog.close());
            confirmButton.setOnAction($ -> {
                project.getGitManager().revertCommit(commit.hash());
                dialog.close();
            });
        });
    }
}
