package io.github.railroad.welcome.project.ui.widget;

import io.github.railroad.welcome.project.ProjectType;
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
        } else {
            this.imageView.setImage(item.getIcon());
            setGraphic(this.imageView);
            setText(item.getName());
        }
    }
}
