package io.github.railroad.plugin.defaults.github.ui;

import io.github.railroad.Railroad;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.vcs.connections.Profile;
import io.github.railroad.vcs.connections.hubs.GithubConnection;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class GithubAccounts extends RRVBox {
    private final RRListView<Profile> profileListView = new RRListView<>();

    public GithubAccounts() {
        // Create the button
        var addButton = new Button("+ Add New Profile");
        addButton.setOnAction(event -> {
            showAddProfileDialog();
        });

        profileListView.setItems(Railroad.REPOSITORY_MANAGER.getProfiles());
        var scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        profileListView.setCellFactory(param -> new GithubProfilesListCell(scrollPane));
        scrollPane.setContent(profileListView);
        RRVBox.setVgrow(scrollPane, Priority.ALWAYS);
        RRHBox.setHgrow(scrollPane, Priority.ALWAYS);
        getChildren().addAll(addButton, scrollPane);
    }

    private void showAddProfileDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Add New Profile");
        RRVBox dialogVbox = new RRVBox(20);
        TextField aliasField = new TextField();
        aliasField.setPromptText("Enter Alias");
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter GitHub token");
        Button addProfileButton = new Button("Add Profile");
        addProfileButton.setOnAction(event -> {
            String username = usernameField.getText();
            if (!username.isEmpty()) {
                Profile profile = new Profile();
                profile.setAccessToken(username);
                GithubConnection connection = new GithubConnection(profile);
                if (connection.validateProfile()) {
                    System.out.println("Valid profile");
                } else {
                    System.out.println("Invalid prof");
                }
                //profileListView.getItems().add(profile);
                dialog.close();
            }
        });

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> dialog.close());
        RRVBox box = new RRVBox();
        box.getChildren().addAll(addProfileButton, cancelButton);
        dialogVbox.getChildren().addAll(aliasField, usernameField, box);
        Scene dialogScene = new Scene(dialogVbox, 300, 200);
        dialog.setScene(dialogScene);
        dialog.show();
    }
}
