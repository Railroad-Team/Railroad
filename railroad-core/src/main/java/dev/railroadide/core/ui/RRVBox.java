package dev.railroadide.core.ui;

import javafx.scene.layout.VBox;

public class RRVBox extends VBox {
    public RRVBox() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "VBox", "background-2");
    }

    public RRVBox(double spacing) {
        super(spacing);
        getStyleClass().addAll("Railroad", "Pane", "VBox", "background-2");
    }
}
