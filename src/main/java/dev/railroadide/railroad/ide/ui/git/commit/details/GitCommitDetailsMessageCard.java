package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRTextArea;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import dev.railroadide.railroad.vcs.git.diff.GitAdditionsDeletions;
import javafx.geometry.Pos;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

import java.util.List;

public class GitCommitDetailsMessageCard extends RRVBox {
    public GitCommitDetailsMessageCard(Project project, GitCommit commit) {
        super();
        getStyleClass().addAll("git-commit-details-message-vbox", "git-commit-details-message-card");

        var messageHeadingHbox = new RRHBox();
        messageHeadingHbox.getStyleClass().add("git-commit-details-message-heading-hbox");

        var messageHeading = new LocalizedText("railroad.git.commit.details.message");
        messageHeading.getStyleClass().add("git-commit-details-message-heading");
        messageHeadingHbox.getChildren().add(messageHeading);

        var spacer = new Region();
        messageHeadingHbox.getChildren().add(spacer);
        RRHBox.setHgrow(spacer, Priority.ALWAYS);

        List<GitAdditionsDeletions> additionsDeletions = project.getGitManager().getAdditionsDeletions(commit.hash());
        int additions = additionsDeletions.stream().mapToInt(GitAdditionsDeletions::additions).sum();
        int deletions = additionsDeletions.stream().mapToInt(GitAdditionsDeletions::deletions).sum();

        var additionsDeletionsText = new Text("+%d -%d".formatted(additions, deletions));
        additionsDeletionsText.getStyleClass().add("git-commit-details-additions-deletions");

        var dotText = new Text("•");
        dotText.getStyleClass().add("git-commit-details-message-dot");

        var filesChangedText = new LocalizedText("railroad.git.commit.details.files_changed", additionsDeletions.size());
        filesChangedText.getStyleClass().add("git-commit-details-files-changed");

        var statsHBox = new RRHBox(6);
        statsHBox.getStyleClass().add("git-commit-details-stats-hbox");
        statsHBox.setAlignment(Pos.CENTER_LEFT);
        statsHBox.getChildren().addAll(additionsDeletionsText, dotText, filesChangedText);
        messageHeadingHbox.getChildren().add(statsHBox);

        getChildren().add(messageHeadingHbox);

        var messageContent = new RRTextArea("railroad.git.commit.details.message_content.placeholder");
        messageContent.getStyleClass().add("git-commit-details-message-content");
        messageContent.setEditable(false);
        messageContent.setWrapText(true);
        messageContent.setText(commit.body());
        getChildren().add(messageContent);
    }
}
