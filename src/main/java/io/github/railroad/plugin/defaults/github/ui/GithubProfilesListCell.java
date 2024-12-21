package io.github.railroad.plugin.defaults.github.ui;

import io.github.railroad.Railroad;
import io.github.railroad.config.ConfigHandler;
import io.github.railroad.plugin.defaults.Github;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRStackPane;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.vcs.connections.Profile;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;

import java.util.Objects;

public class GithubProfilesListCell extends ListCell<Profile> {
    private final RRStackPane node = new RRStackPane();
    private final GithubProfilesListCell.ProfileListNode profileListNode = new GithubProfilesListCell.ProfileListNode();

    public GithubProfilesListCell(ScrollPane pane) {
        getStyleClass().add("project-list-cell");
        node.getChildren().add(profileListNode);

        var ellipseButton = new Button("...");
        ellipseButton.setBackground(null);
        RRStackPane.setAlignment(ellipseButton, Pos.TOP_RIGHT);

        var dropdown = new ContextMenu();
        var removeItem = new MenuItem("Remove");

//        removeItem.setOnAction(event -> {
//            Profile profile = profileListNode.profileProperty().get();
//            if (profile != null) {
//                Github.GithubSettings settings = ConfigHandler.getConfig().getSettings().getPluginSettings("Github", Github.GithubSettings.class);
//                for (Profile account : settings.getAccounts()) {
//                    if (Objects.equals(account, profile)) {
//                        settings.getAccounts().remove(account);
//                        ConfigHandler.saveConfig();
//                    }
//                }
//
//                Railroad.REPOSITORY_MANAGER.deleteProfile(profile);
//            }
//        });

        dropdown.getItems().addAll(removeItem);

        ellipseButton.setOnMouseClicked(event ->
                dropdown.show(ellipseButton, event.getScreenX(), event.getScreenY()));

        node.getChildren().add(ellipseButton);
    }

    @Override
    protected void updateItem(Profile profile, boolean empty) {
        super.updateItem(profile, empty);

        if (empty || profile == null) {
            setText(null);
            setGraphic(null);
            profileListNode.profileProperty().set(null);
        } else {
            profileListNode.profileProperty().set(profile);
            setGraphic(node);
        }
    }

    public static class ProfileListNode extends RRHBox {
        private final ObjectProperty<Profile> profile = new SimpleObjectProperty<>();

        public ProfileListNode() {
            getStyleClass().add("project-list-node");
            setSpacing(5);
            setPadding(new Insets(10));
            setAlignment(Pos.CENTER_LEFT);

            var aliasLabel = new Label();
            aliasLabel.textProperty().bind(profile.map(Profile::getAlias));
            aliasLabel.setStyle("-fx-font-size: 16px;");

            var accessTokenLabel = new Label();
            accessTokenLabel.textProperty().bind(profile.map(Profile::getShortAccessToken));
            accessTokenLabel.setStyle("-fx-font-size: 16px;");

            var icon = new ImageView();
            icon.setFitWidth(32);
            icon.setFitHeight(32);
            icon.setPreserveRatio(true);
            var rrvBox = new RRVBox();
            rrvBox.getChildren().addAll(aliasLabel, accessTokenLabel);
            getChildren().addAll(icon, rrvBox);
        }

        public ProfileListNode(Profile plugin) {
            this();
            this.profile.set(plugin);
        }

        public ObjectProperty<Profile> profileProperty() {
            return profile;
        }
    }
}
