package dev.railroadide.core.ui;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;

public class RRFlowPane extends FlowPane {
    public RRFlowPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    public RRFlowPane(Orientation orientation) {
        super(orientation);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    public RRFlowPane(double hgap, double vgap) {
        super(hgap, vgap);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    public RRFlowPane(Orientation orientation, double hgap, double vgap) {
        super(orientation, hgap, vgap);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    public RRFlowPane(Node... children) {
        super(children);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    public RRFlowPane(Orientation orientation, Node... children) {
        super(orientation, children);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    public RRFlowPane(double hgap, double vgap, Node... children) {
        super(hgap, vgap, children);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }

    public RRFlowPane(Orientation orientation, double hgap, double vgap, Node... children) {
        super(orientation, hgap, vgap, children);
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-2");
    }
}
