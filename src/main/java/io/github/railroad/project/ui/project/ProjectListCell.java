package io.github.railroad.project.ui.project;

import io.github.railroad.project.Project;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

public class ProjectListCell extends ListCell<Project> {
    private final ProjectListNode node = new ProjectListNode();

    public ProjectListCell() {
        getStyleClass().add("project-list-cell");
    }

    @Override
    protected void updateItem(Project project, boolean empty) {
        super.updateItem(project, empty);

        if (empty || project == null) {
            setText(null);
            setGraphic(null);
            node.projectProperty().set(null);
        } else {
            node.projectProperty().set(project);
            setGraphic(node);
        }
    }

    public static class ProjectListNode extends RRVBox {
        private final ObjectProperty<Project> project = new SimpleObjectProperty<>();
        private final ImageView icon;
        private final Label label;
        private final Label pathLabel;
        private final Label lastOpened;

        public ProjectListNode() {
            getStyleClass().add("project-list-node");

            setSpacing(5);
            setPadding(new Insets(10));
            setAlignment(Pos.CENTER_LEFT);

            var icon = new ImageView();
            icon.setFitWidth(32);
            icon.setFitHeight(32);
            icon.setPreserveRatio(true);
            icon.imageProperty().bind(project.map(project -> project.getIcon().orElse(null)));
            this.icon = icon;

            var nameLabel = new Label();
            nameLabel.textProperty().bind(project.map(Project::getAlias));
            nameLabel.setStyle("-fx-font-size: 16px;");
            this.label = nameLabel;

            var pathLabel = new Label();
            pathLabel.textProperty().bind(project.map(Project::getPathStr));
            pathLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #808080;");
            this.pathLabel = pathLabel;

            var lastOpened = new Label();
            lastOpened.textProperty().bind(project.map(Project::getLastOpenedFriendly));
            lastOpened.setStyle("-fx-font-size: 14px; -fx-text-fill: #808080;");
            this.lastOpened = lastOpened;

            var settingsButton = new Button();
            settingsButton.setText("Project Settings");

            var nameBox = new RRHBox(5);
            nameBox.getChildren().addAll(icon, nameLabel);
            nameBox.setAlignment(Pos.CENTER_LEFT);

            getChildren().addAll(nameBox, pathLabel, lastOpened, settingsButton);
        }

        public ProjectListNode(Project project) {
            this();
            this.project.set(project);
        }

        public ObjectProperty<Project> projectProperty() {
            return project;
        }

        public ImageView getIcon() {
            return icon;
        }

        public Label getLabel() {
            return label;
        }
    }
}
