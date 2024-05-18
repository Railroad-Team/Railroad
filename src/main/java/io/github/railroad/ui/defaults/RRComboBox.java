package io.github.railroad.ui.defaults;

import javafx.scene.control.ComboBox;

public class RRComboBox<T> extends ComboBox<T> {
    public RRComboBox(int foreground) {
        super();
        getStyleClass().addAll("Railroad", "ComboBox", "Text", "foreground-" + foreground);
    }

    public RRComboBox() {
        this(1);
    }
}
