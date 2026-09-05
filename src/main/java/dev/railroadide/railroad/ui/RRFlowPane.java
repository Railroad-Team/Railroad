package dev.railroadide.railroad.ui;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;

/**
 * A wrapping flow layout with Railroad's pane and secondary-background style classes.
 */
public class RRFlowPane extends FlowPane {
    /**
     * Creates an empty styled horizontal flow pane with zero gaps.
     */
    public RRFlowPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    /**
     * Creates an empty styled flow pane with zero gaps.
     *
     * @param orientation whether children flow in horizontal rows or vertical columns
     */
    public RRFlowPane(Orientation orientation) {
        super(orientation);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    /**
     * Creates an empty styled horizontal flow pane with the specified gaps.
     *
     * @param hgap horizontal gap between children or columns, in pixels
     * @param vgap vertical gap between children or rows, in pixels
     */
    public RRFlowPane(double hgap, double vgap) {
        super(hgap, vgap);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    /**
     * Creates an empty styled flow pane with the specified orientation and gaps.
     *
     * @param orientation whether children flow in horizontal rows or vertical columns
     * @param hgap horizontal gap between children or columns, in pixels
     * @param vgap vertical gap between children or rows, in pixels
     */
    public RRFlowPane(Orientation orientation, double hgap, double vgap) {
        super(orientation, hgap, vgap);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    /**
     * Creates a styled horizontal flow pane with initial children and zero gaps.
     *
     * @param children initial children in flow order
     */
    public RRFlowPane(Node... children) {
        super(children);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    /**
     * Creates a styled flow pane with initial children and zero gaps.
     *
     * @param orientation whether children flow in horizontal rows or vertical columns
     * @param children initial children in flow order
     */
    public RRFlowPane(Orientation orientation, Node... children) {
        super(orientation, children);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    /**
     * Creates a styled horizontal flow pane with initial children and the specified gaps.
     *
     * @param hgap horizontal gap between children, in pixels
     * @param vgap vertical gap between rows, in pixels
     * @param children initial children in flow order
     */
    public RRFlowPane(double hgap, double vgap, Node... children) {
        super(hgap, vgap, children);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    /**
     * Creates a styled flow pane with initial children, orientation, and gaps.
     *
     * @param orientation whether children flow in horizontal rows or vertical columns
     * @param hgap horizontal gap between children or columns, in pixels
     * @param vgap vertical gap between children or rows, in pixels
     * @param children initial children in flow order
     */
    public RRFlowPane(Orientation orientation, double hgap, double vgap, Node... children) {
        super(orientation, hgap, vgap, children);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }
}
