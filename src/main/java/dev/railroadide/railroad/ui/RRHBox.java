package dev.railroadide.railroad.ui;

import javafx.scene.Node;
import javafx.scene.layout.HBox;

/**
 * A horizontal layout container with Railroad's pane and secondary-background style classes.
 */
public class RRHBox extends HBox {
    /**
     * Creates an empty styled horizontal container with zero spacing.
     */
    public RRHBox() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "HBox", "background-2");
    }

    /**
     * Creates an empty styled horizontal container with the specified spacing.
     *
     * @param spacing horizontal space between adjacent children, in pixels
     */
    public RRHBox(double spacing) {
        super(spacing);
        getStyleClass().addAll("Railroad", "Pane", "HBox", "background-2");
    }

    /**
     * Creates a styled horizontal container with initial children and spacing.
     *
     * @param spacing horizontal space between adjacent children, in pixels
     * @param children initial children in layout order
     */
    public RRHBox(double spacing, Node... children) {
        super(spacing, children);
        getStyleClass().addAll("Railroad", "Pane", "HBox", "background-2");
    }

    /**
     * Creates a styled horizontal container with initial children and zero spacing.
     *
     * @param children initial children in layout order
     */
    public RRHBox(Node... children) {
        super(children);
        getStyleClass().addAll("Railroad", "Pane", "HBox", "background-2");
    }
}
