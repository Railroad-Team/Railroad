package io.github.railroad.ui.defaults;

import io.github.railroad.ui.defaults.interfaces.IRRPane;
import javafx.scene.control.ScrollPane;

public class RRScrollPane extends ScrollPane implements IRRPane {
    public RRScrollPane(int level) {
        super();
        getStyleClass().addAll("Railroad", "Pane", "ScrollPane", "background-" + level);
    }

    public RRScrollPane() {
        this(0);
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
