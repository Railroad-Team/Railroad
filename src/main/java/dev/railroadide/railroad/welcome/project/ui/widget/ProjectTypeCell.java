package dev.railroadide.railroad.welcome.project.ui.widget;

import dev.railroadide.railroad.welcome.project.ProjectType;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;

public class ProjectTypeCell extends ListCell<ProjectType> {
    private final ImageView imageView = new ImageView();

    public ProjectTypeCell() {
        this.imageView.setFitWidth(16);
        this.imageView.setFitHeight(16);
        setFont(Font.font(16));
    }

    @Override
    protected void updateItem(ProjectType item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
            getStyleClass().remove("project-type-cell");
            getStyleClass().remove("selected");
        } else {
            this.imageView.setImage(item.getIcon());
            setGraphic(this.imageView);
            setText(item.getName());
            getStyleClass().add("project-type-cell");
            if (isSelected() || isFocused()) {
                getStyleClass().add("selected");
            } else {
                getStyleClass().remove("selected");
            }
        }
    }
}
