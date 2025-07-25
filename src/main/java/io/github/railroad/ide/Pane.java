package io.github.railroad.ide;


import io.github.railroad.ui.defaults.RRVBox;
import javafx.scene.Node;

import java.io.InputStream;

public abstract class Pane extends RRVBox {

    // Use The GetResourcesAsStream Method On Railroad To get The Logo
    public abstract InputStream getLogo();

    // Displayed Next To Logo
    public abstract String getPaneName();
}
