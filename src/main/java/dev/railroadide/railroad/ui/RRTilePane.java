package dev.railroadide.railroad.ui;

import javafx.scene.layout.TilePane;

/**
 * A uniform-tile layout pane with Railroad's pane and secondary-background style classes.
 */
public class RRTilePane extends TilePane {
    /**
     * Creates an empty styled tile pane with horizontal orientation and zero gaps.
     */
    public RRTilePane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "TilePane", "background-2");
    }
}
