package io.github.railroad.ui.defaults;

import io.github.railroad.ui.defaults.interfaces.IRRPane;
import javafx.scene.layout.HBox;

public class RRHBox extends HBox implements IRRPane {
    public RRHBox(int level, double spacing) {
        super(spacing);
        getStyleClass().addAll("Railroad", "Pane", "HBox", "background-" + level);
    }

    public RRHBox(int level) {
        this(level, 0);
    }

    public RRHBox() {
        this(0, 0);
    }

    @Override
    public void isMainContainer(boolean value) {
        if (value)
            getStyleClass().add("border");
        else
            getStyleClass().remove("border");
    }

    @Override
    public void setBackgroundLevel() {

    }

    @Override
    public void setForegroundLevel() {

    }

    @Override
    public void setBorderLevel() {

    }
}
