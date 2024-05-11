package io.github.railroad.ui.defaults;

import javafx.scene.control.ListCell;

public class RRListCell<T> extends ListCell<T> {
    public RRListCell() {
        super();
        getStyleClass().addAll("Railroad", "ListCell", "foreground-1", "background-none");
    }
}
