package dev.railroadide.core.ui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

public class RRStackPane extends StackPane {
    public RRStackPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "StackPane", "background-2");
    }

    public RRStackPane(Node... children) {
        super(children);
        getStyleClass().addAll("Railroad", "Pane", "StackPane", "background-2");
    }
}
