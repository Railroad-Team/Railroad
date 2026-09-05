package dev.railroadide.railroad.welcome.project.ui.widget;

import dev.railroadide.railroad.project.ProjectType;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.text.Font;

/** Displays a project type's icon and name, applying selection styling when the item is refreshed. */
public class ProjectTypeCell extends ListCell<ProjectType> {
    private final ImageView imageView = new ImageView();

    /** Creates an empty cell with a 16-pixel icon area and font. */
    public ProjectTypeCell() {
        this.imageView.setFitWidth(16);
        this.imageView.setFitHeight(16);
        setFont(Font.font(16));
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
