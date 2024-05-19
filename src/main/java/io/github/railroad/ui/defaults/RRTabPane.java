package io.github.railroad.ui.defaults;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

public class RRTabPane extends VBox {
    private TabPane tabPane;

    public RRTabPane() {
        super();
        tabPane = new TabPane();
        VBox vBox = new VBox(tabPane);
        super.getChildren().add(vBox);
    }

    public ObservableList<Tab> getTabs() {
        return this.tabPane.getTabs();
    }
}
