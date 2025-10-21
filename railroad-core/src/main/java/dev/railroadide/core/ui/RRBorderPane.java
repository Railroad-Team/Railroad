package dev.railroadide.core.ui;

import javafx.scene.layout.BorderPane;

public class RRBorderPane extends BorderPane {
    public RRBorderPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "BorderPane", "background-2");
    }
}