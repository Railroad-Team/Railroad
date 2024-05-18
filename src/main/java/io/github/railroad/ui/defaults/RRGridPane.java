package io.github.railroad.ui.defaults;

import io.github.railroad.ui.defaults.interfaces.IRRPane;
import javafx.scene.layout.GridPane;

public class RRGridPane extends GridPane implements IRRPane {
    public RRGridPane(int level) {
        super();
        getStyleClass().addAll("Railroad", "Pane", "GridPane", "background-" + level);
    }

    public RRGridPane() {
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
