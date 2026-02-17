package dev.railroadide.core.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;

/**
 * Tree cell that renders a checkbox alongside the tree item value.
 */
public class RRCheckBoxTreeCell<T> extends TreeCell<T> {
    private final CheckBox checkBox = new CheckBox();
    private final Label textLabel = new Label();
    private final HBox container = new HBox(8);
    private RRCheckBoxTreeItem<T> boundItem;

    public RRCheckBoxTreeCell() {
        container.setAlignment(Pos.CENTER_LEFT);
        checkBox.getStyleClass().add("rr-tree-view-check-box");
        checkBox.setAllowIndeterminate(false);
        textLabel.getStyleClass().add("rr-tree-view-label");
        container.getChildren().addAll(checkBox, textLabel);
        setText(null);
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            unbindItem();
            setText(null);
            setGraphic(null);
            return;
        }

        bindItem(getTreeItem());

        Node valueNode = item instanceof Node ? (Node) item : null;
        if (valueNode != null) {
            setContent(valueNode, null, null);
        } else {
            String text = item.toString();
            Node treeGraphic = getTreeItem() != null ? getTreeItem().getGraphic() : null;
            setContent(treeGraphic, text, textLabel);
        }

        setText(null);
        setGraphic(container);
    }

    private void bindItem(TreeItem<T> treeItem) {
        unbindItem();
        if (treeItem instanceof RRCheckBoxTreeItem<?> checkBoxItem) {
            @SuppressWarnings("unchecked")
            RRCheckBoxTreeItem<T> typedItem = (RRCheckBoxTreeItem<T>) checkBoxItem;
            boundItem = typedItem;
            checkBox.selectedProperty().bindBidirectional(typedItem.selectedProperty());
            checkBox.indeterminateProperty().bindBidirectional(typedItem.indeterminateProperty());
            checkBox.disableProperty().bind(typedItem.disabledProperty());
        } else {
            checkBox.setSelected(false);
            checkBox.setIndeterminate(false);
            checkBox.setDisable(true);
        }
    }

    private void unbindItem() {
        if (boundItem != null) {
            checkBox.selectedProperty().unbindBidirectional(boundItem.selectedProperty());
            checkBox.indeterminateProperty().unbindBidirectional(boundItem.indeterminateProperty());
            checkBox.disableProperty().unbind();
            boundItem = null;
        }
    }

    protected void setContent(Node graphic, String text, Node textNode) {
        container.getChildren().clear();
        container.getChildren().add(checkBox);

        if (graphic != null) {
            container.getChildren().add(graphic);
        }

        if (textNode != null && text != null && !text.isEmpty()) {
            textLabel.setText(text);
            container.getChildren().add(textNode);
        } else if (textNode != null) {
            textLabel.setText("");
        }
    }

    protected void setCustomContent(Node graphic) {
        setContent(graphic, null, null);
        setText(null);
        setGraphic(container);
    }
}
