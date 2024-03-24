package io.github.railroad.project.ui.project;

import io.github.railroad.project.Project;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

public class ProjectListCell extends ListCell<Project> {
    private final ProjectListNode node = new ProjectListNode();

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

    @Override
    public void updateSelected(boolean selected) {
        super.updateSelected(selected);
        node.setStyle(selected ? "-fx-background-color: #f0f0f0;" : "-fx-background-color: #ffffff;");
        node.getLabel().setTextFill(Color.BLACK);
    }

    public static class ProjectListNode extends HBox {
        private final ObjectProperty<Project> project = new SimpleObjectProperty<>();
        private final ImageView icon;
        private final Label label;

        public ProjectListNode() {
            setSpacing(10);
            setPadding(new Insets(5));
            setAlignment(Pos.CENTER_LEFT);
            setStyle("-fx-background-color: #ffffff;");

            var icon = new ImageView();
            icon.setFitWidth(32);
            icon.setFitHeight(32);
            icon.setPreserveRatio(true);
            icon.imageProperty().bind(project.map(project -> project.getIcon().orElse(null)));
            this.icon = icon;

            var label = new Label();
            label.textProperty().bind(project.map(Project::getAlias));
            label.setStyle("-fx-font-size: 16px;");
            this.label = label;

            getChildren().addAll(icon, label);
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
