package io.github.railroad.ui.defaults;

import javafx.scene.control.SplitPane;

public class RRSplitPane extends SplitPane {
    public RRSplitPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "SplitPane", "contrast-4", "background-2");
    }
}
