package io.github.railroad.core.ui;

import javafx.scene.layout.StackPane;

public class RRStackPane extends StackPane {
    public RRStackPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "StackPane", "background-2");
    }
}
