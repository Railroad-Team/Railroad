package io.github.railroad.ui.defaults;

import javafx.scene.control.Label;

public class RRTitle extends Label {
    public RRTitle() {
        super();
        getStyleClass().addAll("Railroad", "Text", "Title", "foreground-1");
    }

    public RRTitle(String text) {
        this();
        setText(text);
    }
}
