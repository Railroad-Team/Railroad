package io.github.railroad.plugin.defaults.github.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.railroad.utility.ConfigHandler;
import io.github.railroad.vcs.connections.Profile;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;

public class GithubAccounts extends ScrollPane {
    private final ListView<Profile> profileListView = new ListView<>();

    public GithubAccounts() {
        setFitToWidth(true);
        setFitToHeight(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);

        /*final Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(Railroad.getWindow());
        VBox dialogVbox = new VBox(20);
        dialogVbox.getChildren().add(new Text("This is a Dialog"));
        Scene dialogScene = new Scene(dialogVbox, 300, 200);
        dialog.setScene(dialogScene);
        dialog.show();*/

        JsonObject config = ConfigHandler.getPluginSettings("Github", true);
        if (config.has("accounts")) {
            JsonArray accounts = config.get("accounts").getAsJsonArray();
            for (JsonElement account : accounts) {
                if (account.isJsonObject()) {
                    Profile profile = new Profile();
                    profile.setUsername(account.getAsJsonObject().get("username").getAsString());
                    profileListView.getItems().add(profile);
                }
            }
        }
        profileListView.setCellFactory(param -> new GithubProfilesListCell(this));
        setContent(profileListView);
    }
}
