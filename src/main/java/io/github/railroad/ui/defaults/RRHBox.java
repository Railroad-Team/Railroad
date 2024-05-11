package io.github.railroad.ui.defaults;

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
}
