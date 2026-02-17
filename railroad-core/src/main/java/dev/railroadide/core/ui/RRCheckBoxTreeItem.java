package dev.railroadide.core.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree item that exposes a selectable checkbox state for use with RRCheckBoxTreeView.
 */
public class RRCheckBoxTreeItem<T> extends TreeItem<T> {
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected", false);
    private final BooleanProperty indeterminate = new SimpleBooleanProperty(this, "indeterminate", false);
    private final BooleanProperty disabled = new SimpleBooleanProperty(this, "disabled", false);
    private final BooleanProperty propagateSelectionToChildren = new SimpleBooleanProperty(this, "propagateSelectionToChildren", true);
    private final BooleanProperty propagateSelectionToParent = new SimpleBooleanProperty(this, "propagateSelectionToParent", true);
    private boolean updatingState;
    private final ChangeListener<Boolean> childStateListener = (observable, oldValue, newValue) -> updateStateFromChildren();
    private final ListChangeListener<TreeItem<T>> childrenListener = change -> {
        while (change.next()) {
            if (change.wasRemoved()) {
                for (TreeItem<T> removed : change.getRemoved()) {
                    unregisterChild(removed);
                }
            }
            if (change.wasAdded()) {
                for (TreeItem<T> added : change.getAddedSubList()) {
                    registerChild(added);
                }
            }
        }
        updateStateFromChildren();
    };

    public RRCheckBoxTreeItem() {
        super();
        initializeSelectionHandling();
    }

    public RRCheckBoxTreeItem(T value) {
        super(value);
        initializeSelectionHandling();
    }

    public RRCheckBoxTreeItem(T value, Node graphic) {
        super(value, graphic);
        initializeSelectionHandling();
    }

    public void expandAll() {
        setExpanded(true);

        for (TreeItem<?> child : getChildren()) {
            if (child instanceof RRCheckBoxTreeItem<?> checkChild) {
                checkChild.expandAll();
            }
        }
    }

    public void collapseAll() {
        setExpanded(false);
        setIndeterminate(false);
        setSelected(false);

        for (TreeItem<?> child : getChildren()) {
            if (child instanceof RRCheckBoxTreeItem<?> checkChild) {
                checkChild.collapseAll();
            }
        }
    }

    public List<T> getSelectedValues() {
        List<T> selectedChanges = new ArrayList<>();
        collectSelected(selectedChanges);
        return selectedChanges;
    }

    public void clearSelection() {
        setIndeterminate(false);
        setSelected(false);

        for (TreeItem<T> child : getChildren()) {
            if (child instanceof RRCheckBoxTreeItem<T> checkChild) {
                checkChild.clearSelection();
            }
        }
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean isSelected() {
        return selected.get();
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    public BooleanProperty indeterminateProperty() {
        return indeterminate;
    }

    public boolean isIndeterminate() {
        return indeterminate.get();
    }

    public void setIndeterminate(boolean value) {
        if (updatingState) {
            indeterminate.set(value);
            return;
        }

        if (value && hasNoSelectableChildren()) {
            indeterminate.set(false);
            return;
        }

        if (value) {
            setSelectionState(false, true);
            updateParentState();
        } else {
            indeterminate.set(false);
            updateParentState();
        }
    }

    public BooleanProperty disabledProperty() {
        return disabled;
    }

    public boolean isDisabled() {
        return disabled.get();
    }

    public void setDisabled(boolean disabled) {
        this.disabled.set(disabled);
    }

    private void collectSelected(List<T> selectedChanges) {
        T value = getValue();
        if (isSelected() && !isIndeterminate()) {
            selectedChanges.add(value);
        }

        for (TreeItem<T> child : getChildren()) {
            if (child instanceof RRCheckBoxTreeItem<T> checkChild) {
                checkChild.collectSelected(selectedChanges);
            }
        }
    }

    private void initializeSelectionHandling() {
        selected.addListener((observable, oldValue, newValue) -> {
            if (updatingState)
                return;

            updatingState = true;
            try {
                indeterminate.set(false);
                if (propagateSelectionToChildren.get()) {
                    for (TreeItem<T> child : getChildren()) {
                        if (child instanceof RRCheckBoxTreeItem<?> checkChild) {
                            checkChild.setIndeterminate(false);
                            checkChild.setSelected(newValue);
                        }
                    }
                }
            } finally {
                updatingState = false;
            }
            updateParentState();
        });

        indeterminate.addListener((observable, oldValue, newValue) -> {
            if (updatingState || !newValue) {
                if (!updatingState) {
                    updateParentState();
                }

                return;
            }

            if (hasNoSelectableChildren()) {
                setSelectionState(false, false);
                return;
            }

            setSelectionState(false, true);
            updateParentState();
        });

        getChildren().addListener(childrenListener);
        for (TreeItem<T> child : getChildren()) {
            registerChild(child);
        }
    }

    private void registerChild(TreeItem<T> child) {
        if (child instanceof RRCheckBoxTreeItem<?> checkChild) {
            checkChild.selectedProperty().addListener(childStateListener);
            checkChild.indeterminateProperty().addListener(childStateListener);
        }
    }

    private void unregisterChild(TreeItem<T> child) {
        if (child instanceof RRCheckBoxTreeItem<?> checkChild) {
            checkChild.selectedProperty().removeListener(childStateListener);
            checkChild.indeterminateProperty().removeListener(childStateListener);
        }
    }

    private void updateStateFromChildren() {
        if (updatingState)
            return;

        int childCount = 0;
        int selectedCount = 0;
        int indeterminateCount = 0;

        for (TreeItem<T> child : getChildren()) {
            if (child instanceof RRCheckBoxTreeItem<?> checkChild) {
                if (!checkChild.propagateSelectionToParent.get())
                    continue;

                childCount++;
                if (checkChild.isIndeterminate()) {
                    indeterminateCount++;
                } else if (checkChild.isSelected()) {
                    selectedCount++;
                }
            }
        }

        if (childCount == 0)
            return;

        boolean allSelected = selectedCount == childCount && indeterminateCount == 0;
        boolean noneSelected = selectedCount == 0 && indeterminateCount == 0;
        if (allSelected) {
            setSelectionState(true, false);
        } else setSelectionState(false, !noneSelected);

        updateParentState();
    }

    private void setSelectionState(boolean selectedValue, boolean indeterminateValue) {
        if (selected.get() == selectedValue && indeterminate.get() == indeterminateValue)
            return;

        updatingState = true;
        try {
            selected.set(selectedValue);
            indeterminate.set(indeterminateValue);
        } finally {
            updatingState = false;
        }
    }

    private void updateParentState() {
        if (!propagateSelectionToParent.get())
            return;

        TreeItem<T> parent = getParent();
        if (parent instanceof RRCheckBoxTreeItem<?> checkParent) {
            checkParent.updateStateFromChildren();
        }
    }

    private boolean hasNoSelectableChildren() {
        for (TreeItem<T> child : getChildren()) {
            if (child instanceof RRCheckBoxTreeItem<?> checkChild) {
                if (checkChild.propagateSelectionToParent.get())
                    return false;
            }
        }

        return true;
    }
}
