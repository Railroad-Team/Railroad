package io.github.railroad.plugin.defaults.github.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.Railroad;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.ConfigHandler;
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
    private final ListView<Profile> profileListView = new RRListView<>();

    public GithubAccounts() {
        // Create the button
        Button addButton = new Button("+ Add New Profile");
        addButton.setOnAction(event -> {
            showAddProfileDialog();
        });

        /*JsonObject config = ConfigHandler.getPluginSettings("Github", true);
        if (config.has("accounts")) {
            JsonArray accounts = config.get("accounts").getAsJsonArray();
            for (JsonElement account : accounts) {
                if (account.isJsonObject()) {
                    Profile profile = new Profile();
                    profile.setUsername(account.getAsJsonObject().get("username").getAsString());
                    //profileListView.getItems().add(profile);
                }
            }
        }*/

        profileListView.setItems(Railroad.REPOSITORY_MANAGER.getProfiles());
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        profileListView.setCellFactory(param -> new GithubProfilesListCell(scrollPane));
        scrollPane.setContent(profileListView);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        HBox.setHgrow(scrollPane, Priority.ALWAYS);
        getChildren().addAll(addButton, scrollPane);
    }

    private void showAddProfileDialog() {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Add New Profile");
        RRVBox dialogVbox = new RRVBox(20);
        TextField usernameField = new TextField();
        usernameField.setPromptText("Enter GitHub username");
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
        dialogVbox.getChildren().addAll(usernameField, addProfileButton, cancelButton);
        Scene dialogScene = new Scene(dialogVbox, 300, 200);
        dialog.setScene(dialogScene);
        dialog.show();
    }
}
