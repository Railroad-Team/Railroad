package dev.railroadide.railroad.ui;

import javafx.scene.layout.GridPane;

/**
 * A grid layout pane with Railroad's pane and secondary-background style classes.
 */
public class RRGridPane extends GridPane {
    /**
     * Creates an empty styled grid pane.
     */
    public RRGridPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "GridPane", "background-2");
    }
}
