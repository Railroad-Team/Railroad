package dev.railroadide.railroad.ide.ui.git.commit;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.command.CommandButtons;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.GitCommands;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedTooltip;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

/**
 * Provides repository refresh and tree-expansion controls alongside pending rollback and shelf actions.
 */
public class GitCommitHeaderPane extends RRHBox {
    /**
     * Creates repository and tree controls for the selected commit changes.
     *
     * @param project project whose files and workspace are being displayed
     * @param gitCommitChangesPane change-selection pane controlled by the header
     */
    public GitCommitHeaderPane(Project project, GitCommitChangesPane gitCommitChangesPane) {
        Services.UI_MANAGER.assignWhileAttached(UIIds.Git.GIT_COMMIT_HEADER, this);
        RRButton refreshButton = createButton(FontAwesomeSolid.SYNC,
            new String[]{"git-commit-header-button", "sync-button"}, "git.commit.header.refresh.tooltip");
        RRButton rollbackButton = createButton(FontAwesomeSolid.UNDO,
            new String[]{"git-commit-header-button", "undo-button"}, "git.commit.header.rollback.tooltip");
        RRButton shelfButton = createButton(FontAwesomeSolid.BOX,
            new String[]{"git-commit-header-button", "shelf-button"}, "git.commit.header.shelf.tooltip");
        RRButton expandAllButton = createButton(FontAwesomeSolid.EXPAND_ALT,
            new String[]{"git-commit-header-button", "expand-all-button"}, "git.commit.header.expand_all.tooltip");
        RRButton collapseAllButton = createButton(FontAwesomeSolid.COMPRESS_ALT,
            new String[]{"git-commit-header-button", "collapse-all-button"}, "git.commit.header.collapse_all.tooltip");

        getChildren().addAll(
            refreshButton,
            rollbackButton,
            shelfButton,
            expandAllButton,
            collapseAllButton);
        getStyleClass().add("git-commit-header-pane");

        CommandButtons.bind(refreshButton, GitCommands.REFRESH,
            () -> CommandContext.withArgument(null, this, project.getGitManager()));

        CommandButtons.bind(expandAllButton, GitCommands.EXPAND_CHANGES,
            () -> CommandContext.withArgument(project, this, gitCommitChangesPane));
        CommandButtons.bind(collapseAllButton, GitCommands.COLLAPSE_CHANGES,
            () -> CommandContext.withArgument(project, this, gitCommitChangesPane));

        // TODO: Implement rollback and shelve functionality
        // rollbackButton.setOnAction(event ->
        // project.getGitManager().rollbackChanges(gitCommitChangesPane.getSelectedChanges()));
        // shelfButton.setOnAction(event ->
        // project.getGitManager().shelveChanges(gitCommitChangesPane.getSelectedChanges()));
    }

    private static RRButton createButton(Ikon ikon, String[] styleClass, String tooltipKey) {
        var button = new RRButton();
        button.getStyleClass().addAll(styleClass);
        button.setIcon(ikon);
        button.setSquare(true);
        button.setVariant(ButtonVariant.GHOST);
        button.setTooltip(new LocalizedTooltip(tooltipKey));
        return button;
    }
}
