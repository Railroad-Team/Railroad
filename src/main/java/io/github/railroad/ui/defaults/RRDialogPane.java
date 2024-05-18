package io.github.railroad.ui.defaults;

import io.github.railroad.ui.defaults.interfaces.IRRPane;
import javafx.scene.control.DialogPane;

public class RRDialogPane extends DialogPane implements IRRPane {
    public RRDialogPane(int level) {
        super();
        getStyleClass().addAll("Railroad", "Pane", "DialogPane", "background-" + level);
    }

    public RRDialogPane() {
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
