package io.github.railroad.ui.defaults;

import io.github.railroad.ui.defaults.interfaces.IRRPane;
import javafx.scene.layout.FlowPane;

public class RRFlowPane extends FlowPane implements IRRPane {
    public RRFlowPane(int level) {
        super();
        getStyleClass().addAll("Railroad", "Pane", "FlowPane", "background-" + level);
    }

    public RRFlowPane() {
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
