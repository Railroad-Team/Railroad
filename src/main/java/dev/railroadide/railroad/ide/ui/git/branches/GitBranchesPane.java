package dev.railroadide.railroad.ide.ui.git.branches;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRTextField;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class GitBranchesPane extends RRVBox {
    private final TextField searchBar;
    private final GitLocalBranchesListView localBranchesListView;
    private final GitRemoteBranchesListView remoteBranchesListView;

    public GitBranchesPane(Project project) {
        searchBar = new RRTextField("railroad.git.branches.search.placeholder");
        searchBar.getStyleClass().add("git-branches-search-bar");

        localBranchesListView = new GitLocalBranchesListView(project);
        remoteBranchesListView = new GitRemoteBranchesListView(project);

        HBox localHeader = createSectionHeader("railroad.git.branches.local", localBranchesListView);
        HBox remoteHeader = createSectionHeader("railroad.git.branches.remote", remoteBranchesListView);

        getChildren().addAll(searchBar, localHeader, localBranchesListView, remoteHeader, remoteBranchesListView);
        getStyleClass().add("git-branches-pane");
        setAlignment(Pos.TOP_LEFT);
        setSpacing(10);
        setPadding(new Insets(5));

        searchBar.textProperty().addListener((observable, oldValue, newValue) -> {
            localBranchesListView.filterBranches(newValue);
            remoteBranchesListView.filterBranches(newValue);
        });
    }

    private static HBox createSectionHeader(String localizationKey, javafx.scene.control.ListView<?> listView) {
        HBox header = new HBox(8);
        header.getStyleClass().add("git-branches-section-header");

        LocalizedLabel title = new LocalizedLabel(localizationKey);
        title.getStyleClass().add("git-branches-section-title");

        Label count = new Label();
        count.getStyleClass().add("git-branches-section-count");
        count.textProperty().bind(Bindings.size(listView.getItems()).asString());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(title, spacer, count);
        return header;
    }
}
