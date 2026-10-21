package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.railroad.command.CommandButtons;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.GitCommands;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import dev.railroadide.railroad.vcs.git.status.GitRepoStatus;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Provides an action to check out a commit with handling for uncommitted changes.
 */
public class GitCommitCheckoutButton extends RRButton {
    /**
     * Creates the action for checking out the supplied commit.
     *
     * @param project project whose files and workspace are being displayed
     * @param commit commit to display or act on
     */
    public GitCommitCheckoutButton(Project project, GitCommit commit) {
        super("railroad.git.commit.details.button.checkout_commit", FontAwesomeSolid.CHECK);
        setVariant(ButtonVariant.PRIMARY);
        CommandButtons.bind(this, GitCommands.CHECKOUT,
            () -> CommandContext.withArgument(project, this, commit));
    }

    private static void onCheckoutWithUncommittedChanges(
        GitManager gitManager,
        Optional<GitCommit> fromCommit,
        GitCommit toCommit
    ) {
        GitRepoStatus repoStatus = gitManager.getRepoStatus();

        var content = new RRVBox(2);
        content.getStyleClass().add("git-commit-checkout-uncommitted-changes-dialog-content");

        var infoText = new LocalizedText("railroad.git.commit.details.checkout_commit_dialog.uncommitted_changes_info");
        infoText.getStyleClass().add("git-commit-checkout-uncommitted-changes-info-text");
        content.getChildren().add(infoText);

        var unstagedChangesText = new LocalizedText(
            "railroad.git.commit.details.checkout_commit_dialog.unstaged_changes",
            repoStatus.changes().stream().filter(GitFileChange::isUnstaged).count());
        unstagedChangesText.getStyleClass().add("git-commit-checkout-unstaged-changes-text");
        content.getChildren().add(unstagedChangesText);

        var stagedChangesText = new LocalizedText(
            "railroad.git.commit.details.checkout_commit_dialog.staged_changes",
            repoStatus.changes().stream().filter(GitFileChange::isStaged).count());
        stagedChangesText.getStyleClass().add("git-commit-checkout-staged-changes-text");
        content.getChildren().add(stagedChangesText);

        var untrackedChangesText = new LocalizedText(
            "railroad.git.commit.details.checkout_commit_dialog.untracked_changes",
            repoStatus.changes().stream().filter(GitFileChange::isUntracked).count());
        untrackedChangesText.getStyleClass().add("git-commit-checkout-untracked-changes-text");
        content.getChildren().add(untrackedChangesText);

        var cancelButton = new RRButton("railroad.generic.cancel");
        cancelButton.setVariant(ButtonVariant.SECONDARY);
        cancelButton.getStyleClass().add("git-commit-checkout-uncommitted-changes-cancel-button");

        var stashAndCheckoutButton = new RRButton(
            "railroad.git.commit.details.checkout_commit_dialog.stash_and_checkout");
        stashAndCheckoutButton.setVariant(ButtonVariant.PRIMARY);
        stashAndCheckoutButton.getStyleClass().add("git-commit-checkout-uncommitted-changes-stash-and-checkout-button");

        var forceCheckoutButton = new RRButton("railroad.git.commit.details.checkout_commit_dialog.force_checkout");
        forceCheckoutButton.setVariant(ButtonVariant.DANGER);
        forceCheckoutButton.getStyleClass().add("git-commit-checkout-uncommitted-changes-force-checkout-button");

        DialogBuilder dialogBuilder = DialogBuilder.create()
            .title("railroad.git.commit.details.checkout_commit_dialog.subtitle")
            .contentNode(content)
            .buttons(cancelButton, stashAndCheckoutButton, forceCheckoutButton);
        Stage dialog = WindowBuilder.createDialog("railroad.git.commit.details.checkout_commit_dialog.title",
            dialogBuilder);

        cancelButton.setOnAction(_ -> dialog.close());

        stashAndCheckoutButton.setOnAction(_ -> {
            gitManager.stashChanges("Railroad: before checkout " + fromCommit.map(GitCommit::shortHash).orElse("HEAD"),
                true);
            gitManager.checkoutCommit(toCommit.hash());
            dialog.close();
        });

        forceCheckoutButton.setOnAction(_ -> {
            var discardTextField = new RRTextField(
                "railroad.git.commit.details.checkout_commit_dialog.force_checkout.confirmation_placeholder");
            discardTextField.getStyleClass().add("git-commit-checkout-force-checkout-confirmation-text-field");

            var forceContent = new RRVBox(10);
            forceContent.getStyleClass().add("git-commit-checkout-force-checkout-dialog-content");
            var forceInfoText = new LocalizedText(
                "railroad.git.commit.details.checkout_commit_dialog.force_checkout_info");
            forceInfoText.getStyleClass().add("git-commit-checkout-force-checkout-info-text");
            forceContent.getChildren().add(forceInfoText);
            forceContent.getChildren().add(discardTextField);

            AtomicReference<Stage> forceDialogRef = new AtomicReference<>();
            DialogBuilder forceDialogBuilder = DialogBuilder.create()
                .title("railroad.git.commit.details.checkout_commit_dialog.force_checkout_title")
                .contentNode(forceContent)
                .onCancel(() -> {
                    Stage forceDialogStage = forceDialogRef.get();
                    if (forceDialogStage != null) {
                        forceDialogStage.close();
                    }
                })
                .onConfirm(() -> {
                    if (discardTextField.getText().equals("FORCE")) {
                        gitManager.resetHard();
                        gitManager.cleanUntrackedFiles();
                        gitManager.checkoutCommit(toCommit.hash());
                        Stage forceDialogStage = forceDialogRef.get();
                        if (forceDialogStage != null) {
                            forceDialogStage.close();
                        }
                        dialog.close();
                    }
                });

            Stage forceDialog = WindowBuilder.createDialog(
                "railroad.git.commit.details.checkout_commit_dialog.force_checkout_title",
                forceDialogBuilder);
            forceDialogRef.set(forceDialog);

            var confirmButton = (RRButton) forceDialog.getScene().lookup(".rr-button.primary");
            if (confirmButton != null) {
                confirmButton.setDisable(true);
                discardTextField.textProperty()
                    .addListener((obs, oldText, newText) -> confirmButton.setDisable(!newText.equals("FORCE")));
            }

        });
    }

    /**
     * Runs the existing checkout workflow.
     *
     * @param project project owning the repository
     * @param commit target commit
     */
    public static void execute(Project project, GitCommit commit) {
        GitManager gitManager = project.getGitManager();
        if (!gitManager.getRepoStatus().changes().isEmpty()) {
            onCheckoutWithUncommittedChanges(gitManager, gitManager.getCurrentCommit(), commit);
            return;
        }

        gitManager.checkoutCommit(commit.hash());
    }
}
