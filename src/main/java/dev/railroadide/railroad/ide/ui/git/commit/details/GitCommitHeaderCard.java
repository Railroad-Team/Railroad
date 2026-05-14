package dev.railroadide.railroad.ide.ui.git.commit.details;

import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.utility.TimeFormatter;
import dev.railroadide.railroad.vcs.git.commit.GitCommit;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class GitCommitHeaderCard extends RRVBox {
    private final Timeline committedAnimation;

    public GitCommitHeaderCard(GitCommit commit) {
        super(6);
        getStyleClass().add("git-commit-header-card");

        var subject = new Text(commit.subject());
        subject.getStyleClass().add("git-commit-details-subject");

        var detailsHBox = new RRHBox(2);
        detailsHBox.getStyleClass().add("git-commit-details-hbox");
        detailsHBox.setAlignment(Pos.CENTER_LEFT);

        var authorText = new LocalizedText("railroad.git.commit.details.author", commit.authorName());
        authorText.getStyleClass().addAll("git-commit-details-author", "git-commit-details-meta-text");
        detailsHBox.getChildren().add(authorText);

        var committedText = new LocalizedText(
            "railroad.git.commit.details.committed",
            TimeFormatter.formatElapsed(commit.authorTimestampEpochSeconds() * 1000L)
        );
        committedAnimation = new Timeline(new KeyFrame(
            Duration.seconds(1),
            event -> committedText.setKeyAndArgs(
                "railroad.git.commit.details.committed",
                TimeFormatter.formatElapsed(commit.authorTimestampEpochSeconds() * 1000L)
            )
        ));
        committedAnimation.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                committedAnimation.stop();
            } else {
                committedAnimation.play();
            }
        });
        committedText.getStyleClass().addAll("git-commit-details-committed-time", "git-commit-details-meta-text");
        detailsHBox.getChildren().add(committedText);

        var hashText = new LocalizedText("railroad.git.commit.details.hash", commit.hash());
        hashText.getStyleClass().addAll("git-commit-details-hash", "git-commit-details-meta-text");
        detailsHBox.getChildren().add(hashText);

        getChildren().addAll(subject, detailsHBox);
    }
}
