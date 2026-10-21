package dev.railroadide.railroad.ui;

import javafx.scene.Node;
import javafx.scene.layout.VBox;

/**
 * A vertical layout container with Railroad's pane and secondary-background style classes.
 */
public class RRVBox extends VBox {
    /**
     * Creates an empty styled vertical container with zero spacing.
     */
    public RRVBox() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "VBox", "background-2");
    }

    /**
     * Creates an empty styled vertical container with the specified spacing.
     *
     * @param spacing vertical space between adjacent children, in pixels
     */
    public RRVBox(double spacing) {
        super(spacing);
        getStyleClass().addAll("Railroad", "Pane", "VBox", "background-2");
    }

    /**
     * Creates a styled vertical container with initial children and spacing.
     *
     * @param spacing vertical space between adjacent children, in pixels
     * @param children initial children in layout order
     */
    public RRVBox(double spacing, Node... children) {
        super(spacing, children);
        getStyleClass().addAll("Railroad", "Pane", "VBox", "background-2");
    }

    /**
     * Creates a styled vertical container with initial children and zero spacing.
     *
     * @param children initial children in layout order
     */
    public RRVBox(Node... children) {
        super(children);
        getStyleClass().addAll("Railroad", "Pane", "VBox", "background-2");
    }
}
