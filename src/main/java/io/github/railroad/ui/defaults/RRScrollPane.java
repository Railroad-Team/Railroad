package io.github.railroad.ui.defaults;

import javafx.scene.control.ScrollPane;

public class RRScrollPane extends ScrollPane {
    public RRScrollPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "ScrollPane", "background-2");
    }
}
