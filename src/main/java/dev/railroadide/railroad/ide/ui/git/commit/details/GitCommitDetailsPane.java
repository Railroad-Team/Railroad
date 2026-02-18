package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;

import java.util.List;
import java.util.Map;

public class GitCommitDetailsPane extends RRVBox {
    public static final String DEFAULT_TITLE = "Commit Details";

    private final StringProperty title = new SimpleStringProperty(DEFAULT_TITLE);
    private final ObjectProperty<GitCommit> commit = new SimpleObjectProperty<>();
    private final RailroadProject project;

    private String headCommitHash = "";
    private Map<String, List<String>> tagsByCommit = Map.of();

    public GitCommitDetailsPane(RailroadProject project) {
        super();
        this.project = project;
        getStyleClass().add("git-commit-details-root");

        setAlignment(Pos.CENTER);
        getChildren().add(createEmptyState());

        commit.addListener((obs, oldCommit, newCommit) -> updateCommitDetails(newCommit));

        reloadCommitMetadata();
        project.getGitManager().commitMetadataRevisionProperty().addListener((obs, oldRevision, newRevision) -> reloadCommitMetadata());
    }

    private LocalizedText createEmptyState() {
        var emptyState = new LocalizedText("railroad.git.commit.details.no_commit_selected");
        emptyState.getStyleClass().add("git-commit-details-empty-state");
        return emptyState;
    }

    private void updateCommitDetails(GitCommit newCommit) {
        getChildren().clear();

        if (newCommit == null) {
            getChildren().add(createEmptyState());
            setAlignment(Pos.CENTER);
            title.set(DEFAULT_TITLE);
            return;
        }

        getChildren().add(new GitCommitDetailsView(project, newCommit, headCommitHash, tagsByCommit));
        setAlignment(Pos.TOP_CENTER);
        title.set("Commit: " + newCommit.shortHash());
    }

    private void reloadCommitMetadata() {
        project.getGitManager().getCommitListMetadata().thenAccept(metadata -> Platform.runLater(() -> {
            headCommitHash = metadata.headCommitHash();
            tagsByCommit = metadata.tagsByCommit();

            GitCommit currentCommit = commit.get();
            if (currentCommit != null) {
                updateCommitDetails(currentCommit);
            }
        }));
    }

    public StringProperty titleProperty() {
        return title;
    }

    public void setCommit(GitCommit commit) {
        this.commit.set(this.project.getGitManager().getCommitWithBody(commit));
    }
}
