package io.github.railroad.ui.defaults;

import io.github.railroad.ui.defaults.interfaces.IRRPane;
import javafx.scene.layout.VBox;

public class RRVBox extends VBox implements IRRPane {
    public RRVBox(int level, double spacing) {
        super(spacing);
        getStyleClass().addAll("Railroad", "Pane", "VBox", "background-" + level);
    }

    public RRVBox(int level) {
        this(level, 0);
    }

    public RRVBox() {
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

