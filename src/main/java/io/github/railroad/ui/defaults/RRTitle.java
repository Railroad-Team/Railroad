package io.github.railroad.ui.defaults;

import javafx.scene.control.Label;

public class RRTitle extends Label {
    public RRTitle() {
        super();
        getStyleClass().addAll("Railroad", "Text", "Header-1", "foreground-1");
    }

    public RRTitle(String text) {
        this();
        setText(text);
    }
}
