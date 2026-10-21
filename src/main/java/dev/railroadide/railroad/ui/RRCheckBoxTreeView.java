package dev.railroadide.railroad.ui;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/**
 * Tree view that renders items using {@link RRCheckBoxTreeCell} and Railroad styling.
 * Use {@link RRCheckBoxTreeItem} values to provide selectable checkboxes.
 *
 * @param <T> the type of value stored in each tree item
 */
public class RRCheckBoxTreeView<T> extends TreeView<T> {
    /**
     * CSS classes installed when the tree view is initialized.
     */
    public static final String[] DEFAULT_STYLE_CLASSES = {"rr-check-box-tree-view", "tree-view"};

    /**
     * Creates a styled checkbox tree with no root.
     */
    public RRCheckBoxTreeView() {
        super();
        initialize();
    }

    /**
     * Creates a styled checkbox tree with the supplied root.
     *
     * @param root the tree root, or null for an empty tree
     */
    public RRCheckBoxTreeView(TreeItem<T> root) {
        super(root);
        initialize();
    }

    private void initialize() {
        getStyleClass().setAll(DEFAULT_STYLE_CLASSES);
        setCellFactory(view -> new RRCheckBoxTreeCell<>());
    }
}
