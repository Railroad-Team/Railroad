package dev.railroadide.railroad.welcome.project.ui.widget;

import dev.railroadide.railroad.project.ProjectType;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;

/** Displays a project type's icon and name; native cell states drive selection styling. */
public class ProjectTypeCell extends ListCell<ProjectType> {
    private final ImageView imageView = new ImageView();

    /** Creates an empty cell with a 16-pixel icon area. */
    public ProjectTypeCell() {
        this.imageView.setFitWidth(16);
        this.imageView.setFitHeight(16);
        getStyleClass().add("project-type-cell");
        setGraphicTextGap(10);
    }

    /**
     * Updates the name, icon, and selection styling, clearing them when the cell has no item.
     *
     * @param item project type to display, or null
     * @param empty whether the cell has no item
     */
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
