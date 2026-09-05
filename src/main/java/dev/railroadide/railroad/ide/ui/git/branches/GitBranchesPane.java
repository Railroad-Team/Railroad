package dev.railroadide.railroad.ide.ui.git.branches;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.id.UIIds;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Displays searchable local and remote branch lists for a project.
 */
public class GitBranchesPane extends RRVBox {
    private final TextField searchBar;
    private final GitLocalBranchesListView localBranchesListView;
    private final GitRemoteBranchesListView remoteBranchesListView;

    /**
     * Creates searchable local and remote branch sections for the project.
     *
     * @param project project whose files and workspace are being displayed
     */
    public GitBranchesPane(Project project) {
        Services.UI_MANAGER.assignWhileAttached(UIIds.Git.GIT_BRANCHES, this);
        searchBar = new RRTextField("railroad.git.branches.search.placeholder");
        searchBar.getStyleClass().add("git-branches-search-bar");

        localBranchesListView = new GitLocalBranchesListView(project);
        remoteBranchesListView = new GitRemoteBranchesListView(project);

        HBox localHeader = createSectionHeader("railroad.git.branches.local", localBranchesListView);
        HBox remoteHeader = createSectionHeader("railroad.git.branches.remote", remoteBranchesListView);

        getChildren().addAll(searchBar, localHeader, localBranchesListView, remoteHeader, remoteBranchesListView);
        getStyleClass().add("git-branches-pane");
        setAlignment(Pos.TOP_LEFT);

        searchBar.textProperty().addListener((_, _, newValue) -> {
            localBranchesListView.filterBranches(newValue);
            remoteBranchesListView.filterBranches(newValue);
        });
    }

    private static HBox createSectionHeader(String localizationKey, ListView<?> listView) {
        var header = new HBox();
        header.getStyleClass().add("git-branches-section-header");

        var title = new LocalizedLabel(localizationKey);
        title.getStyleClass().add("git-branches-section-title");

        var count = new Label();
        count.getStyleClass().add("git-branches-section-count");
        count.textProperty().bind(Bindings.size(listView.getItems()).asString());

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(title, spacer, count);
        return header;
    }
}
