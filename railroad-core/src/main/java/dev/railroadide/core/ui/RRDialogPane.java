package dev.railroadide.core.ui;

import javafx.scene.control.DialogPane;

public class RRDialogPane extends DialogPane {
    public RRDialogPane() {
        super();
        getStyleClass().addAll("Railroad", "Pane", "DialogPane", "background-2");
    }
}
