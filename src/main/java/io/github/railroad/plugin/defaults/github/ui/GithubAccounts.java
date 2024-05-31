package io.github.railroad.plugin.defaults.github.ui;

import io.github.railroad.Railroad;
import io.github.railroad.plugin.defaults.Github;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRListView;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.vcs.connections.Profile;
import io.github.railroad.vcs.connections.hubs.GithubConnection;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

public class GithubAccounts extends RRVBox {
    private final RRListView<Profile> profileListView = new RRListView<>();

    public GithubAccounts() {
        // Create the button
        var addButton = new Button("+ Add New Profile");
        addButton.setOnAction(event -> showAddProfileDialog());

        profileListView.setItems(FXCollections.observableArrayList(Railroad.REPOSITORY_MANAGER.getProfiles()));

        var scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        profileListView.setCellFactory(param -> new GithubProfilesListCell(scrollPane));

        scrollPane.setContent(profileListView);
        getChildren().addAll(addButton, scrollPane);
        RRVBox.setVgrow(scrollPane, Priority.ALWAYS);
        RRHBox.setHgrow(scrollPane, Priority.ALWAYS);
    }

    private void showAddProfileDialog() {
        var dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Add New Profile");

        var dialogVbox = new RRVBox(20);

        var aliasField = new TextField();
        aliasField.setPromptText("Enter Alias");

        var usernameField = new TextField();
        usernameField.setPromptText("Enter GitHub token");

        var addProfileButton = createAddButton(usernameField, aliasField, dialog);

        var cancelButton = new Button("Cancel");
        cancelButton.setOnAction(event -> dialog.close());

        var box = new RRVBox();
        box.getChildren().addAll(addProfileButton, cancelButton);
        dialogVbox.getChildren().addAll(aliasField, usernameField, box);

        var dialogScene = new Scene(dialogVbox, 300, 200);
        dialog.setScene(dialogScene);
        dialog.show();
    }

    private static @NotNull Button createAddButton(TextField usernameField, TextField aliasField, Stage dialog) {
        var addProfileButton = new Button("Add Profile");
        addProfileButton.setOnAction(event -> {
            String username = usernameField.getText();
            if (!username.isEmpty()) {
                var profile = new Profile();
                profile.usernameProperty().set(username);
                profile.aliasProperty().set(aliasField.getText());
                var connection = new GithubConnection(profile);
                if (connection.validateProfile()) {
                    Railroad.LOGGER.debug("Valid GitHub profile");
                    Railroad.REPOSITORY_MANAGER.addConnection(connection);
                } else {
                    Railroad.LOGGER.debug("Invalid GitHub profile");
                }

                // profileListView.getItems().add(profile);
                dialog.close();
            }
        });
        
        return addProfileButton;
    }
}
