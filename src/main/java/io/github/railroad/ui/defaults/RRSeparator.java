package io.github.railroad.ui.defaults;

import javafx.scene.control.Separator;

public class RRSeparator extends Separator {
    public RRSeparator(int level, boolean fullWidth) {
        super();
        getStyleClass().addAll("Railroad", "Separator", "contrast-" + level);
        if (fullWidth) getStyleClass().add("full");
    }

    public RRSeparator(int level) {
        this(level, false);
    }

    public RRSeparator(boolean fullWidth) {
        this(4, fullWidth);
    }

    public RRSeparator() {
        this(4, false);
    }
}
