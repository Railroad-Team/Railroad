package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import dev.railroadide.railroad.vcs.git.status.GitFileChange;
import dev.railroadide.railroad.vcs.git.status.GitRepoStatus;
import dev.railroadide.railroad.vcs.git.util.CherryPickResult;
import dev.railroadide.railroad.window.AlertType;
import dev.railroadide.railroad.window.DialogBuilder;
import dev.railroadide.railroad.window.WindowBuilder;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class GitCommitCherryPickButton extends RRButton {
    public GitCommitCherryPickButton(Project project, GitCommit commit) {
        super("railroad.git.commit.details.button.cherry_pick", FontAwesomeSolid.MAGNET);
        setVariant(ButtonVariant.PRIMARY);
        setOnAction(event -> {
            GitManager gitManager = project.getGitManager();
            if (!gitManager.getRepoStatus().changes().isEmpty()) {
                CompletableFuture<boolean[]> canContinue = confirmCherryPickWithUncommittedChanges(gitManager, gitManager.getCurrentCommit(), commit);
                canContinue.thenAccept(canContinueResult -> {
                    boolean canContinueCherryPick = canContinueResult[0];
                    boolean shouldStash = canContinueResult[1];
                    if (canContinueCherryPick) {
                        continueCherryPick(gitManager, commit, shouldStash);
                    }
                });

                return;
            }

            continueCherryPick(gitManager, commit, false);
        });
    }

    private static void continueCherryPick(GitManager gitManager, GitCommit commit, boolean stashedChanges) {
        if (gitManager.isInCherryPickState()) {
            var content = new LocalizedText("railroad.git.commit.details.cherry_pick.error.already_cherry_picking");
            content.getStyleClass().add("git-commit-cherry-pick-already-cherry-picking-content");

            var continueButton = new RRButton("railroad.git.commit.details.cherry_pick.error.already_cherry_picking_continue");
            continueButton.setVariant(ButtonVariant.PRIMARY);
            continueButton.getStyleClass().add("git-commit-cherry-pick-already-cherry-picking-continue-button");

            var abortButton = new RRButton("railroad.git.commit.details.cherry_pick.error.already_cherry_picking_abort");
            abortButton.setVariant(ButtonVariant.SECONDARY);
            abortButton.getStyleClass().add("git-commit-cherry-pick-already-cherry-picking-abort-button");

            var cancelButton = new RRButton("railroad.generic.cancel");
            cancelButton.setVariant(ButtonVariant.SECONDARY);
            cancelButton.getStyleClass().add("git-commit-cherry-pick-already-cherry-picking-cancel-button");

            DialogBuilder dialogBuilder = DialogBuilder.create()
                .title("railroad.git.commit.details.cherry_pick.error.already_cherry_picking_title")
                .contentNode(content)
                .buttons(continueButton, abortButton, cancelButton);
            Stage dialog = WindowBuilder.createDialog("railroad.git.commit.details.cherry_pick.error.already_cherry_picking_title", dialogBuilder);

            continueButton.setOnAction($ -> {
                if (!canContinueCherryPick(gitManager))
                    return;

                gitManager.continueCherryPick();
                dialog.close();
            });

            abortButton.setOnAction($ -> {
                gitManager.abortCherryPick();
                if(stashedChanges) {
                    gitManager.stashPop();
                }

                dialog.close();
            });

            cancelButton.setOnAction($ -> dialog.close());

            return;
        }

        CompletableFuture<CherryPickResult> cherryPickFuture = gitManager.cherryPickCommit(commit.hash());
        cherryPickFuture.thenAccept(result -> {
            if (result == CherryPickResult.CONFLICTS) {
                Platform.runLater(() -> showCherryPickConflictsDialog(gitManager));
                return;
            }

            if (result == CherryPickResult.FAILED) {
                Platform.runLater(() -> WindowBuilder.createAlert(
                        AlertType.ERROR,
                        "railroad.git.commit.details.cherry_pick.error.title",
                        "railroad.git.commit.details.cherry_pick.error.subtitle",
                        "railroad.git.commit.details.cherry_pick.error.content"
                    )
                    .build());
                return;
            }

            // TODO: Show success notification
        });
    }

    private static void showCherryPickConflictsDialog(GitManager gitManager) {
        var content = new LocalizedText("railroad.git.commit.details.cherry_pick.conflicts.content");
        content.getStyleClass().add("git-commit-cherry-pick-conflicts-content");

        var continueButton = new RRButton("railroad.git.commit.details.cherry_pick.conflicts.continue");
        continueButton.setVariant(ButtonVariant.PRIMARY);
        continueButton.getStyleClass().add("git-commit-cherry-pick-conflicts-continue-button");

        var abortButton = new RRButton("railroad.git.commit.details.cherry_pick.conflicts.abort");
        abortButton.setVariant(ButtonVariant.SECONDARY);
        abortButton.getStyleClass().add("git-commit-cherry-pick-conflicts-abort-button");

        var cancelButton = new RRButton("railroad.generic.cancel");
        cancelButton.setVariant(ButtonVariant.SECONDARY);
        cancelButton.getStyleClass().add("git-commit-cherry-pick-conflicts-cancel-button");

        DialogBuilder dialogBuilder = DialogBuilder.create()
            .title("railroad.git.commit.details.cherry_pick.conflicts.title")
            .contentNode(content)
            .buttons(continueButton, abortButton, cancelButton);
        Stage dialog = WindowBuilder.createDialog("railroad.git.commit.details.cherry_pick.conflicts.title", dialogBuilder);

        continueButton.setOnAction($ -> {
            if (!canContinueCherryPick(gitManager))
                return;

            gitManager.continueCherryPick();
            dialog.close();
        });

        abortButton.setOnAction($ -> {
            gitManager.abortCherryPick();
            dialog.close();
        });

        cancelButton.setOnAction($ -> dialog.close());

    }

    private static boolean canContinueCherryPick(GitManager gitManager) {
        GitRepoStatus repoStatus = gitManager.getRepoStatus();
        if (repoStatus == null)
            return true;

        boolean hasUnresolvedConflicts = repoStatus.changes().stream().anyMatch(GitFileChange::isConflict);
        if (!hasUnresolvedConflicts)
            return true;

        WindowBuilder.createAlert(
                AlertType.WARNING,
                "railroad.git.commit.details.cherry_pick.unresolved.title",
                "railroad.git.commit.details.cherry_pick.unresolved.subtitle",
                "railroad.git.commit.details.cherry_pick.unresolved.content"
            )
            .build();
        return false;
    }

    private static CompletableFuture<boolean[]> confirmCherryPickWithUncommittedChanges(
        GitManager gitManager,
        Optional<GitCommit> currentCommit,
        GitCommit commit
    ) {
        CompletableFuture<boolean[]> canContinueRef = new CompletableFuture<>();
        GitRepoStatus repoStatus = gitManager.getRepoStatus();

        Platform.runLater(() -> {
            var content = new RRVBox(2);
            content.getStyleClass().add("git-commit-cherry-pick-uncommitted-changes-dialog-content");

            var infoText = new LocalizedText("railroad.git.commit.details.cherry_pick_dialog.uncommitted_changes_info");
            infoText.getStyleClass().add("git-commit-cherry-pick-uncommitted-changes-info-text");
            content.getChildren().add(infoText);

            var unstagedChangesText = new LocalizedText(
                "railroad.git.commit.details.cherry_pick_dialog.unstaged_changes",
                repoStatus.changes().stream().filter(GitFileChange::isUnstaged).count()
            );
            unstagedChangesText.getStyleClass().add("git-commit-cherry-pick-unstaged-changes-text");
            content.getChildren().add(unstagedChangesText);

            var stagedChangesText = new LocalizedText(
                "railroad.git.commit.details.cherry_pick_dialog.staged_changes",
                repoStatus.changes().stream().filter(GitFileChange::isStaged).count()
            );
            stagedChangesText.getStyleClass().add("git-commit-cherry-pick-staged-changes-text");
            content.getChildren().add(stagedChangesText);

            var untrackedChangesText = new LocalizedText(
                "railroad.git.commit.details.cherry_pick_dialog.untracked_changes",
                repoStatus.changes().stream().filter(GitFileChange::isUntracked).count()
            );
            untrackedChangesText.getStyleClass().add("git-commit-cherry-pick-untracked-changes-text");
            content.getChildren().add(untrackedChangesText);

            var cancelButton = new RRButton("railroad.generic.cancel");
            cancelButton.setVariant(ButtonVariant.SECONDARY);
            cancelButton.getStyleClass().add("git-commit-cherry-pick-uncommitted-changes-cancel-button");

            var stashAndContinueButton = new RRButton("railroad.git.commit.details.cherry_pick_dialog.stash_and_continue");
            stashAndContinueButton.setVariant(ButtonVariant.PRIMARY);
            stashAndContinueButton.getStyleClass().add("git-commit-cherry-pick-uncommitted-changes-stash-and-continue-button");

            DialogBuilder dialogBuilder = DialogBuilder.create()
                .title("railroad.git.commit.details.cherry_pick_dialog.subtitle")
                .contentNode(content)
                .buttons(cancelButton, stashAndContinueButton);
            Stage dialog = WindowBuilder.createDialog("railroad.git.commit.details.cherry_pick_dialog.title", dialogBuilder);

            cancelButton.setOnAction($ -> {
                canContinueRef.complete(new boolean[] {false, false});
                dialog.close();
            });

            stashAndContinueButton.setOnAction($ -> {
                gitManager.stashChanges("Railroad: before cherry-pick " + currentCommit.map(GitCommit::shortHash).orElse("HEAD"), true);
                canContinueRef.complete(new boolean[] {true, true});
                dialog.close();
            });

        });

        return canContinueRef;
    }
}
