package dev.railroadide.railroad.ui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

/**
 * A stacking layout pane with Railroad's pane and secondary-background style classes.
 */
public class RRStackPane extends StackPane {
    /**
     * Creates an empty styled stack pane.
     */
    public RRStackPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "StackPane", "background-2");
    }

    /**
     * Creates a styled pane that stacks the supplied children in order.
     *
     * @param children initial children, with the last child drawn on top
     */
    public RRStackPane(Node... children) {
        super(children);
        getStyleClass().addAll("Railroad", "Pane", "StackPane", "background-2");
    }
}
