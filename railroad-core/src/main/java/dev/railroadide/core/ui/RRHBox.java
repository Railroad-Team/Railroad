package dev.railroadide.core.ui;

import javafx.scene.Node;
import javafx.scene.layout.HBox;

public class RRHBox extends HBox {
    public RRHBox() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "HBox", "background-2");
    }

    public RRHBox(double spacing) {
        super(spacing);
        getStyleClass().addAll("Railroad", "Pane", "HBox", "background-2");
    }

    public RRHBox(double spacing, Node... children) {
        super(spacing, children);
        getStyleClass().addAll("Railroad", "Pane", "HBox", "background-2");
    }

    public RRHBox(Node... children) {
        super(children);
        getStyleClass().addAll("Railroad", "Pane", "HBox", "background-2");
    }
}
