package dev.railroadide.railroad.ide.ui.git.overview;

import dev.railroadide.railroad.project.RailroadProject;
import dev.railroadide.railroad.ui.RRGridPane;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.vcs.git.GitManager;
import dev.railroadide.railroad.vcs.git.identity.GitIdentity;
import dev.railroadide.railroad.vcs.git.identity.GitSigningStatus;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class GitOverviewIdentityPane extends RRVBox {
    private final GridPane identityGrid = new RRGridPane();
    private final Text userNameText = new Text();
    private final Text userEmailText = new Text();
    private final Text signedText = new Text();
    private final Text gitVersionText = new Text();

    public GitOverviewIdentityPane(RailroadProject project) {
        getStyleClass().add("git-overview-identity-pane");

        configureGrid();
        VBox.setVgrow(identityGrid, Priority.ALWAYS);
        getChildren().add(identityGrid);

        GitManager gitManager = project.getGitManager();
        updateIdentityInfo(gitManager);
        listenForUpdates(gitManager);
    }

    private void configureGrid() {
        identityGrid.getStyleClass().add("git-overview-identity-grid");
        identityGrid.setHgap(12);
        identityGrid.setVgap(0); // Set vgap to 0 because separators will provide vertical spacing

        var col1 = new ColumnConstraints();
        col1.setHgrow(Priority.NEVER);
        col1.setPrefWidth(Region.USE_COMPUTED_SIZE);
        col1.setMinWidth(Region.USE_PREF_SIZE);
        col1.setMaxWidth(Region.USE_PREF_SIZE);
        var col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);
        identityGrid.getColumnConstraints().addAll(col1, col2);

        int row = 0;

        userNameText.getStyleClass().add("git-overview-identity-value-text");
        userEmailText.getStyleClass().add("git-overview-identity-value-text");
        signedText.getStyleClass().add("git-overview-identity-value-text");
        gitVersionText.getStyleClass().add("git-overview-identity-value-text");

        identityGrid.add(new LocalizedText("railroad.git.overview.identity.user.label"), 0, row);
        GridPane.setValignment(identityGrid.getChildren().get(identityGrid.getChildren().size() - 1), VPos.CENTER);
        identityGrid.add(new TextFlow(userNameText), 1, row);
        GridPane.setValignment(identityGrid.getChildren().get(identityGrid.getChildren().size() - 1), VPos.CENTER);
        row++;
        Region separator0 = new Region();
        separator0.getStyleClass().add("git-overview-grid-row-separator");
        separator0.setMaxWidth(Double.MAX_VALUE); // Ensure the separator stretches
        GridPane.setMargin(separator0, new Insets(4, 0, 4, 0));
        identityGrid.add(separator0, 0, row, 2, 1);
        row++;

        identityGrid.add(new LocalizedText("railroad.git.overview.identity.email.label"), 0, row);
        GridPane.setValignment(identityGrid.getChildren().get(identityGrid.getChildren().size() - 1), VPos.CENTER);
        identityGrid.add(new TextFlow(userEmailText), 1, row);
        GridPane.setValignment(identityGrid.getChildren().get(identityGrid.getChildren().size() - 1), VPos.CENTER);
        row++;
        Region separator1 = new Region();
        separator1.getStyleClass().add("git-overview-grid-row-separator");
        separator1.setMaxWidth(Double.MAX_VALUE); // Ensure the separator stretches
        GridPane.setMargin(separator1, new Insets(4, 0, 4, 0));
        identityGrid.add(separator1, 0, row, 2, 1);
        row++;

        identityGrid.add(new LocalizedText("railroad.git.overview.identity.signing.label"), 0, row);
        GridPane.setValignment(identityGrid.getChildren().get(identityGrid.getChildren().size() - 1), VPos.CENTER);
        identityGrid.add(new TextFlow(signedText), 1, row);
        GridPane.setValignment(identityGrid.getChildren().get(identityGrid.getChildren().size() - 1), VPos.CENTER);
        row++;
        Region separator2 = new Region();
        separator2.getStyleClass().add("git-overview-grid-row-separator");
        separator2.setMaxWidth(Double.MAX_VALUE); // Ensure the separator stretches
        GridPane.setMargin(separator2, new Insets(4, 0, 4, 0));
        identityGrid.add(separator2, 0, row, 2, 1);
        row++;

        identityGrid.add(new LocalizedText("railroad.git.overview.identity.git_version.label"), 0, row);
        GridPane.setValignment(identityGrid.getChildren().get(identityGrid.getChildren().size() - 1), VPos.CENTER);
        identityGrid.add(new TextFlow(gitVersionText), 1, row);
        GridPane.setValignment(identityGrid.getChildren().get(identityGrid.getChildren().size() - 1), VPos.CENTER);
        // No separator after the last row
    }

    private void updateIdentityInfo(GitManager gitManager) {
        GitIdentity identity = gitManager.getIdentity();
        Platform.runLater(() -> {
            if (identity == null) {
                userNameText.setText("Not Set");
                userEmailText.setText("Not Set");
                signedText.setText("Not Configured");
                gitVersionText.setText("Unknown");
            } else {
                String userName = identity.userName();
                String userEmail = identity.email();
                GitSigningStatus signingStatus = identity.signing();
                String gitVersion = identity.gitVersion();

                userNameText.setText(userName != null ? userName : "Not Set");
                userEmailText.setText(userEmail != null ? userEmail : "Not Set");
                signedText.setText(signingStatus != null ? signingStatus.toString() : "Not Configured");
                gitVersionText.setText(gitVersion != null ? gitVersion : "Unknown");
            }
        });
    }

    private void listenForUpdates(GitManager gitManager) {
        gitManager.gitIdentityProperty().addListener((obs, oldIdentity, newIdentity) ->
            updateIdentityInfo(gitManager));
    }
}
