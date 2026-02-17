package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.core.ui.RRFlowPane;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GitCommitDetailsInfoCard extends RRVBox {
    public GitCommitDetailsInfoCard(GitCommit commit, String headCommitHash, Map<String, List<String>> tagsByCommit) {
        super(5);
        getStyleClass().addAll("git-commit-details-info-vbox", "git-commit-details-info-card");
        setAlignment(Pos.TOP_LEFT);

        getChildren().add(createParentsRow(commit));
        getChildren().add(createCommitterRow(commit));
        getChildren().add(createRefsRow(commit, headCommitHash, tagsByCommit));
    }

    private RRHBox createParentsRow(GitCommit commit) {
        var parentsHbox = new RRHBox(4);
        parentsHbox.getStyleClass().add("git-commit-details-info-row");

        var parentsText = new LocalizedText("railroad.git.commit.details.parents");
        parentsText.getStyleClass().add("git-commit-details-parents-label");
        parentsHbox.getChildren().add(parentsText);

        var parentHashesFlow = new RRFlowPane(4, 4);
        parentHashesFlow.getStyleClass().add("git-commit-details-parent-hashes-flow");
        for (var parentHash : commit.parentHashes()) {
            parentHashesFlow.getChildren().add(createChip(parentHash, "git-commit-details-parent-hash"));
        }
        parentsHbox.getChildren().add(parentHashesFlow);
        HBox.setHgrow(parentHashesFlow, Priority.ALWAYS);

        return parentsHbox;
    }

    private RRHBox createCommitterRow(GitCommit commit) {
        var committerHbox = new RRHBox(2);
        committerHbox.getStyleClass().add("git-commit-details-info-row");

        var committerText = new LocalizedText("railroad.git.commit.details.committer");
        committerText.getStyleClass().add("git-commit-details-committer-label");
        committerHbox.getChildren().add(committerText);

        var committerFlow = new RRFlowPane(4, 4);
        committerFlow.getStyleClass().add("git-commit-details-committer-flow");
        committerFlow.getChildren().add(createChip(commit.committerName(), "git-commit-details-committer-name"));
        committerFlow.getChildren().add(createChip("<" + commit.committerEmail() + ">", "git-commit-details-committer-email"));
        committerHbox.getChildren().add(committerFlow);
        HBox.setHgrow(committerFlow, Priority.ALWAYS);

        return committerHbox;
    }

    private RRHBox createRefsRow(GitCommit commit, String headCommitHash, Map<String, List<String>> tagsByCommit) {
        var refsHbox = new RRHBox(4);
        refsHbox.getStyleClass().add("git-commit-details-info-row");

        var refsText = new LocalizedText("railroad.git.commit.details.refs");
        refsText.getStyleClass().add("git-commit-details-refs-label");
        refsHbox.getChildren().add(refsText);

        var refsFlow = new RRFlowPane(4, 4);
        refsFlow.getStyleClass().add("git-commit-details-refs-flow");
        List<String> tags = new ArrayList<>(tagsByCommit.getOrDefault(commit.hash(), List.of()));
        if (commit.hash().equals(headCommitHash)) {
            tags.add("HEAD");
        }

        for (String ref : tags) {
            refsFlow.getChildren().add(createChip(ref, "git-commit-details-ref"));
        }

        refsHbox.getChildren().add(refsFlow);
        HBox.setHgrow(refsFlow, Priority.ALWAYS);

        return refsHbox;
    }

    private RRHBox createChip(String text, String styleClass) {
        var valueText = (text == null || text.isBlank())
            ? new LocalizedText("railroad.git.commit.details.unknown")
            : new Text(text);
        valueText.getStyleClass().add(styleClass);

        var chip = new RRHBox(4);
        chip.getStyleClass().add("git-commit-details-chip");
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getChildren().add(valueText);
        return chip;
    }
}
