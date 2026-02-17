package dev.railroadide.railroad.ide.ui.git.commit;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.localized.LocalizedTooltip;
import dev.railroadide.core.ui.styling.ButtonVariant;
import dev.railroadide.railroad.project.Project;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;

public class GitCommitHeaderPane extends RRHBox {
    public GitCommitHeaderPane(Project project, GitCommitChangesPane gitCommitChangesPane) {
        RRButton refreshButton = createButton(FontAwesomeSolid.SYNC, new String[]{"git-commit-header-button", "sync-button"}, "git.commit.header.refresh.tooltip");
        RRButton rollbackButton = createButton(FontAwesomeSolid.UNDO, new String[]{"git-commit-header-button", "undo-button"}, "git.commit.header.rollback.tooltip");
        RRButton shelfButton = createButton(FontAwesomeSolid.BOX, new String[]{"git-commit-header-button", "shelf-button"}, "git.commit.header.shelf.tooltip");
        RRButton expandAllButton = createButton(FontAwesomeSolid.EXPAND_ALT, new String[]{"git-commit-header-button", "expand-all-button"}, "git.commit.header.expand_all.tooltip");
        RRButton collapseAllButton = createButton(FontAwesomeSolid.COMPRESS_ALT, new String[]{"git-commit-header-button", "collapse-all-button"}, "git.commit.header.collapse_all.tooltip");

        getChildren().addAll(
            refreshButton,
            rollbackButton,
            shelfButton,
            expandAllButton,
            collapseAllButton
        );
        setSpacing(8);
        getStyleClass().add("git-commit-header-pane");

        refreshButton.setOnAction(event ->
            project.getGitManager().refreshStatus());

        expandAllButton.setOnAction(event ->
            gitCommitChangesPane.expandAll());
        collapseAllButton.setOnAction(event ->
            gitCommitChangesPane.collapseAll());

        // TODO: Implement rollback and shelve functionality
//        rollbackButton.setOnAction(event ->
//            project.getGitManager().rollbackChanges(gitCommitChangesPane.getSelectedChanges()));
//        shelfButton.setOnAction(event ->
//            project.getGitManager().shelveChanges(gitCommitChangesPane.getSelectedChanges()));
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
