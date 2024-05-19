package io.github.railroad.ui.defaults;

import javafx.scene.control.Button;
import javafx.scene.layout.VBox;

public class RRQuickRun extends VBox {
    public RRQuickRun() {
        getChildren().addAll(new Button(), new Button());
    }
}
