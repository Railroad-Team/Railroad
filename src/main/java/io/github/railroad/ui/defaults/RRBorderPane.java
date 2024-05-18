package io.github.railroad.ui.defaults;

import io.github.railroad.ui.defaults.interfaces.IRRPane;
import javafx.scene.layout.BorderPane;

public class RRBorderPane extends BorderPane implements IRRPane {
    public RRBorderPane(int level) {
        super();
        getStyleClass().addAll("Railroad", "Pane", "BorderPane", "background-" + level);
    }

    public RRBorderPane() {
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