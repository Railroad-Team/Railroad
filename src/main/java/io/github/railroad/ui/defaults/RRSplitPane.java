package io.github.railroad.ui.defaults;

import io.github.railroad.ui.defaults.interfaces.IRRPane;
import javafx.scene.control.SplitPane;

public class RRSplitPane extends SplitPane implements IRRPane {
    public RRSplitPane(int level) {
        super();
        getStyleClass().addAll("Railroad", "Pane", "SplitPane", "contrast-4", "background-" + level);
    }

    public RRSplitPane() {
        this(0);
    }

    @Override
    public void isMainContainer(boolean value) {

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
