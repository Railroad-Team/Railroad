package io.github.railroad.welcome.project.ui.widget;

import io.github.railroad.Railroad;
import io.github.railroad.project.Project;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.ShutdownHooks;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import lombok.Getter;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ProjectListCell extends ListCell<Project> {
    private final StackPane node = new StackPane();
    private final ProjectListNode projectListNode = new ProjectListNode();

    public ProjectListCell() {
        getStyleClass().add("project-list-cell");
        node.getChildren().add(projectListNode);

        var ellipseButton = new Button("...");
        ellipseButton.setBackground(null);
        StackPane.setAlignment(ellipseButton, Pos.TOP_RIGHT);

        var dropdown = new ContextMenu();
        var openItem = new MenuItem("Open");
        var removeItem = new MenuItem("Remove");

        openItem.setOnAction(e -> {
            Project project = projectListNode.projectProperty().get();
            if (project != null) {
                project.open();
            }
        });

        removeItem.setOnAction(e -> {
            Project project = projectListNode.projectProperty().get();
            if (project != null) {
                Railroad.PROJECT_MANAGER.removeProject(project);
            }
        });

        dropdown.getItems().addAll(openItem, removeItem);

        ellipseButton.setOnMouseClicked(e -> {
            dropdown.show(ellipseButton, e.getScreenX(), e.getScreenY());
        });

        node.getChildren().add(ellipseButton);
    }

    @Override
    protected void updateItem(Project project, boolean empty) {
        super.updateItem(project, empty);

        if (empty || project == null) {
            setText(null);
            setGraphic(null);
            projectListNode.projectProperty().set(null);
        } else {
            projectListNode.projectProperty().set(project);
            setGraphic(node);
        }
    }

    public static class ProjectListNode extends RRVBox {
        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        private final ObjectProperty<Project> project = new SimpleObjectProperty<>();
        @Getter
        private final ImageView icon;
        @Getter
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
            icon.imageProperty().bind(project.flatMap(Project::iconProperty));
            this.icon = icon;

            var nameLabel = new Label();
            nameLabel.textProperty().bind(project.flatMap(Project::aliasProperty));
            nameLabel.setStyle("-fx-font-size: 16px;");
            this.label = nameLabel;

            var pathLabel = new Label();
            pathLabel.textProperty().bind(project.map(Project::getPathString));
            pathLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #808080;");
            this.pathLabel = pathLabel;

            var lastOpened = new Label();
            executor.scheduleAtFixedRate(() -> {
                Project project = this.project.get();
                if (project != null) {
                    Platform.runLater(() -> lastOpened.setText(Project.getLastOpenedFriendly(project.getLastOpened())));
                }
            }, 0, 1, TimeUnit.SECONDS);
            lastOpened.setStyle("-fx-font-size: 14px; -fx-text-fill: #808080;");
            this.lastOpened = lastOpened;

            var nameBox = new RRHBox(5);
            nameBox.getChildren().addAll(icon, nameLabel);
            nameBox.setAlignment(Pos.CENTER_LEFT);

            getChildren().addAll(nameBox, pathLabel, lastOpened);

            ShutdownHooks.addHook(executor::shutdownNow);
        }

        public ProjectListNode(Project project) {
            this();
            this.project.set(project);
        }

        public ObjectProperty<Project> projectProperty() {
            return project;
        }
    }
}
