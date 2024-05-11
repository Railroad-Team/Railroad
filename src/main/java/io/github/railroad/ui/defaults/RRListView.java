package io.github.railroad.ui.defaults;

import javafx.scene.control.ListView;

public class RRListView<T> extends ListView<T> {
    public RRListView() {
        super();
        getStyleClass().addAll("Railroad", "List", "ListView", "foreground-1", "background-2");
    }
}
