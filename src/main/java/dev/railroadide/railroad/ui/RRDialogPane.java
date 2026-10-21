package dev.railroadide.railroad.ui;

import javafx.scene.control.DialogPane;

/**
 * A dialog pane with Railroad's pane and secondary-background style classes.
 */
public class RRDialogPane extends DialogPane {
    /**
     * Creates an empty styled dialog pane.
     */
    public RRDialogPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "DialogPane", "background-2");
    }
}
