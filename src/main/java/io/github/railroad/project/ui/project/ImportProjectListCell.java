package io.github.railroad.project.ui.project;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.vcs.Repository;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;

public class ImportProjectListCell extends ListCell<Repository> {
    private final StackPane node = new StackPane();
    private final ImportProjectListCell.repositoryListNode repositoryListNode = new ImportProjectListCell.repositoryListNode();

    public ImportProjectListCell() {
        getStyleClass().add("project-list-cell");
        node.getChildren().add(repositoryListNode);

        var ellipseButton = new Button("...");
        ellipseButton.setBackground(null);
        StackPane.setAlignment(ellipseButton, Pos.TOP_RIGHT);

        var dropdown = new ContextMenu();
        var openItem = new MenuItem("Clone");
        var removeItem = new MenuItem("Remove");

        openItem.setOnAction(e -> {
            Repository repository = repositoryListNode.repositoryProperty().get();
            if (repository != null) {
                repository.cloneRepo();
            }
        });

        removeItem.setOnAction(e -> {

        });

        dropdown.getItems().addAll(openItem, removeItem);

        ellipseButton.setOnMouseClicked(e -> {
            dropdown.show(ellipseButton, e.getScreenX(), e.getScreenY());
        });

        node.getChildren().add(ellipseButton);
    }

    @Override
    protected void updateItem(Repository repository, boolean empty) {
        super.updateItem(repository, empty);

        if (empty || repository == null) {
            setText(null);
            setGraphic(null);
            repositoryListNode.repositoryProperty().set(null);
        } else {
            repositoryListNode.repositoryProperty().set(repository);
            setGraphic(node);
        }
    }

    public static class repositoryListNode extends RRVBox {
        private final ObjectProperty<Repository> repository = new SimpleObjectProperty<>();
        private final ImageView icon;
        private final Label label;
        private final Label pathLabel;
        private final Label lastOpened;

        public repositoryListNode() {
            getStyleClass().add("project-list-node");

            setSpacing(5);
            setPadding(new Insets(10));
            setAlignment(Pos.CENTER_LEFT);

            var icon = new ImageView();
            icon.setFitWidth(32);
            icon.setFitHeight(32);
            icon.setPreserveRatio(true);
            icon.imageProperty().bind(repository.map(project -> project.getIcon().orElse(null)));
            this.icon = icon;

            var nameLabel = new Label();
            nameLabel.textProperty().bind(repository.map(Repository::getRepositoryName));
            nameLabel.setStyle("-fx-font-size: 16px;");
            this.label = nameLabel;

            var pathLabel = new Label();
            pathLabel.textProperty().bind(repository.map(Repository::getRepositoryURL));
            pathLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #808080;");
            this.pathLabel = pathLabel;

            var lastOpened = new Label();
            lastOpened.textProperty().bind(repository.map(Repository::getRepositoryCloneURL));
            lastOpened.setStyle("-fx-font-size: 14px; -fx-text-fill: #808080;");
            this.lastOpened = lastOpened;

            var nameBox = new RRHBox(5);
            nameBox.getChildren().addAll(icon, nameLabel);
            nameBox.setAlignment(Pos.CENTER_LEFT);

            getChildren().addAll(nameBox, pathLabel, lastOpened);
        }

        public repositoryListNode(Repository repository) {
            this();
            this.repository.set(repository);
        }

        public ObjectProperty<Repository> repositoryProperty() {
            return repository;
        }

        public ImageView getIcon() {
            return icon;
        }

        public Label getLabel() {
            return label;
        }
    }
}