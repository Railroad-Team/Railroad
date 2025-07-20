package io.github.railroad.core.ui;

import javafx.scene.layout.GridPane;

public class RRGridPane extends GridPane {
    public RRGridPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "GridPane", "background-2");
    }
}
