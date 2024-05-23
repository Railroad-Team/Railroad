package io.github.railroad.plugin.defaults.github.ui;

import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.vcs.connections.Profile;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

public class GithubProfilesListCell extends ListCell<Profile> {
    private final StackPane node = new StackPane();
    private final GithubProfilesListCell.ProfileListNode pluginListNode = new GithubProfilesListCell.ProfileListNode();

    public GithubProfilesListCell(ScrollPane pane) {

        getStyleClass().add("project-list-cell");
        node.getChildren().add(pluginListNode);
    }

    @Override
    protected void updateItem(Profile profile, boolean empty) {
        super.updateItem(profile, empty);

        if (empty || profile == null) {
            setText(null);
            setGraphic(null);
            pluginListNode.pluginProperty().set(null);
        } else {
            pluginListNode.pluginProperty().set(profile);
            setGraphic(node);
        }
    }

    public static class ProfileListNode extends RRVBox {
        private final ObjectProperty<Profile> profile = new SimpleObjectProperty<>();

        public ProfileListNode() {
            getStyleClass().add("project-list-node");

            setSpacing(5);
            setPadding(new Insets(10));
            setAlignment(Pos.CENTER_LEFT);

            var nameLabel = new Label();
            nameLabel.textProperty().bind(profile.map(Profile::getUsername));
            nameLabel.setStyle("-fx-font-size: 16px;");

            var icon = new ImageView();
            icon.setFitWidth(32);
            icon.setFitHeight(32);
            icon.setPreserveRatio(true);

            getChildren().addAll(icon, nameLabel);

        }

        public ProfileListNode(Profile plugin) {
            this();
            this.profile.set(plugin);
        }

        public ObjectProperty<Profile> pluginProperty() {
            return profile;
        }
    }
}
