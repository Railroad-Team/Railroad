package io.github.railroad.ui.defaults;

import io.github.railroad.ui.defaults.interfaces.IRRPane;
import javafx.scene.layout.AnchorPane;

public class RRAnchorPane extends AnchorPane implements IRRPane {
    public RRAnchorPane(int level) {
        super();
        getStyleClass().addAll("Railroad", "Pane", "AnchorPane", "background-" + level);
    }

    public RRAnchorPane() {
        this(0);
    }

    // TODO to implement
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