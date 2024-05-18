package io.github.railroad.ui.defaults;

import javafx.scene.control.Label;

public class RRHeader extends Label {
    public RRHeader() {
        this(1);
    }

    public RRHeader(int level, String text) {
        this(level);
        setText(text);
    }

    public RRHeader(int level) {
        super();
        getStyleClass().addAll("Railroad", "Text", "Header-"  + level, "foreground-1");
    }
}
