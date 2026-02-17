package dev.railroadide.core.ui;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/**
 * Tree view that renders a checkbox for each item using RRCheckBoxTreeCell.
 */
public class RRCheckBoxTreeView<T> extends TreeView<T> {
    public static final String[] DEFAULT_STYLE_CLASSES = { "rr-check-box-tree-view", "tree-view" };

    public RRCheckBoxTreeView() {
        super();
        initialize();
    }

    public RRCheckBoxTreeView(TreeItem<T> root) {
        super(root);
        initialize();
    }

    private void initialize() {
        getStyleClass().setAll(DEFAULT_STYLE_CLASSES);
        setCellFactory(view -> new RRCheckBoxTreeCell<>());
    }
}
