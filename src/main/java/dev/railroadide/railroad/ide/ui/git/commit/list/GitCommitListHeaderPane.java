package dev.railroadide.railroad.ide.ui.git.commit.list;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.vcs.git.GitManager;

/**
 * Provides search and filtering controls for the commit history list.
 */
public class GitCommitListHeaderPane extends RRVBox {
    private final RRTextField searchField;

    /**
     * Creates search and repository filters connected to a commit list.
     *
     * @param gitManager repository service supplying state and Git operations
     * @param commitListView commit list whose search and filters the header controls
     */
    public GitCommitListHeaderPane(GitManager gitManager, GitCommitListViewPane commitListView) {
        super();
        Services.UI_MANAGER.assignWhileAttached(UIIds.Git.GIT_COMMIT_LIST_HEADER, this);
        getStyleClass().add("git-commit-list-header-pane");

        var searchVbox = new RRVBox(2);
        searchVbox.getStyleClass().add("git-commit-search-container");

        var searchLabel = new LocalizedText("railroad.git.commit.search.label");
        searchLabel.getStyleClass().add("git-commit-search-label");

        this.searchField = new RRTextField("railroad.git.commit.search.placeholder");
        this.searchField.getStyleClass().add("git-commit-search-field");
        this.searchField.textProperty().addListener((obs, oldText, newText) -> commitListView.setSearchFilter(newText));

        searchVbox.getChildren().addAll(searchLabel, this.searchField);
        getChildren().add(searchVbox);
    }
}
