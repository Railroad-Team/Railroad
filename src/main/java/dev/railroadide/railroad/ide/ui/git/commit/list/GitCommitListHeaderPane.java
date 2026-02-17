package dev.railroadide.railroad.ide.ui.git.commit.list;

import dev.railroadide.core.ui.RRTextField;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.railroad.vcs.git.GitManager;

public class GitCommitListHeaderPane extends RRVBox {
    private final RRTextField searchField;

    public GitCommitListHeaderPane(GitManager gitManager, GitCommitListViewPane commitListView) {
        super();
        getStyleClass().add("git-commit-list-header-pane");

        var searchVbox = new RRVBox(2);
        searchVbox.getStyleClass().add("git-commit-search-container");

        var searchLabel = new LocalizedText("railroad.git.commit.search.label");
        searchLabel.getStyleClass().add("git-commit-search-label");

        this.searchField = new RRTextField("railroad.git.commit.search.placeholder");
        this.searchField.getStyleClass().add("git-commit-search-field");
        this.searchField.textProperty().addListener((obs, oldText, newText) ->
            commitListView.setSearchFilter(newText));

        searchVbox.getChildren().addAll(searchLabel, this.searchField);
        getChildren().add(searchVbox);
    }
}
