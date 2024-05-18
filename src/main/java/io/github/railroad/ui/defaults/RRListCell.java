package io.github.railroad.ui.defaults;

import javafx.scene.control.ListCell;

public class RRListCell<T> extends ListCell<T> {
    public RRListCell(boolean hoverable) {
        super();
        getStyleClass().addAll("Railroad", "ListItem", "Text", "foreground-1", "background-none");
        if (hoverable) getStyleClass().add("hoverable");
    }

    public RRListCell()
    {
        this(false);
    }


    public void enableHovering()
    {
        getStyleClass().add("hoverable");
    }

    public void disableHovering()
    {
        getStyleClass().remove("hoverable");
    }
}
