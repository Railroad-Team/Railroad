package dev.railroadide.railroad.ui;

import javafx.scene.layout.BorderPane;

/**
 * A border layout pane with Railroad's pane and secondary-background style classes.
 */
public class RRBorderPane extends BorderPane {
    /**
     * Creates a styled border pane with all five layout regions empty.
     */
    public RRBorderPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "BorderPane", "background-2");
    }
}
