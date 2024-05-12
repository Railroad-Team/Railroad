package io.github.railroad.ui.defaults;

import javafx.scene.control.Label;

public class RRLabel extends Label {
    public RRLabel() {
        super();
        getStyleClass().addAll("Railroad", "Text", "Label", "foreground-1");
    }

    public RRLabel(String text)  {
        this();
        setText(text);
    }
}
