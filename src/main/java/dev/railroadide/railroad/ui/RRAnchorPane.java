package dev.railroadide.railroad.ui;

import javafx.scene.layout.AnchorPane;

/**
 * An anchor layout pane with Railroad's pane and secondary-background style classes.
 */
public class RRAnchorPane extends AnchorPane {
    /**
     * Creates an empty styled anchor pane.
     */
    public RRAnchorPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "AnchorPane", "background-2");
    }
}
