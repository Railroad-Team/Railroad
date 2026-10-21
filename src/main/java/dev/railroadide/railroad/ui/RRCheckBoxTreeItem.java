package dev.railroadide.railroad.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Tree item with a selectable checkbox state for use with {@link RRCheckBoxTreeView}.
 * Selection propagates through checkbox-item children, and mixed child selection makes parents indeterminate.
 *
 * @param <T> the type of value stored in each tree item
 */
public class RRCheckBoxTreeItem<T> extends TreeItem<T> {
    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected", false);
    private final BooleanProperty indeterminate = new SimpleBooleanProperty(this, "indeterminate", false);
    private final BooleanProperty disabled = new SimpleBooleanProperty(this, "disabled", false);
    private final BooleanProperty propagateSelectionToChildren = new SimpleBooleanProperty(this,
        "propagateSelectionToChildren", true);
    private final BooleanProperty propagateSelectionToParent = new SimpleBooleanProperty(this,
        "propagateSelectionToParent", true);
    private boolean updatingState;
    private final ChangeListener<Boolean> childStateListener = (
        observable,
        oldValue,
        newValue) -> updateStateFromChildren();
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

    /**
     * Creates an unselected tree item with no value or graphic.
     */
    public RRCheckBoxTreeItem() {
        super();
        initializeSelectionHandling();
    }

    /**
     * Creates an unselected tree item with a value.
     *
     * @param value the item value
     */
    public RRCheckBoxTreeItem(T value) {
        super(value);
        initializeSelectionHandling();
    }

    /**
     * Creates an unselected tree item with a value and graphic.
     *
     * @param value the item value
     * @param graphic the item's graphic, or null
     */
    public RRCheckBoxTreeItem(T value, Node graphic) {
        super(value, graphic);
        initializeSelectionHandling();
    }

    /**
     * Expands this item and recursively expands descendants reached through checkbox tree items.
     */
    public void expandAll() {
        setExpanded(true);

        for (TreeItem<?> child : getChildren()) {
            if (child instanceof RRCheckBoxTreeItem<?> checkChild) {
                checkChild.expandAll();
            }
        }
    }

    /**
     * Collapses this item and checkbox-item descendants, clearing their selected and indeterminate states.
     */
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

    /**
     * Collects values from fully selected checkbox items, including this item and selected parent items.
     *
     * @return a new list in depth-first order, excluding indeterminate items
     */
    public List<T> getSelectedValues() {
        List<T> selectedChanges = new ArrayList<>();
        collectSelected(selectedChanges);
        return selectedChanges;
    }

    /**
     * Clears selected and indeterminate state for this item and its checkbox-item descendants.
     */
    public void clearSelection() {
        setIndeterminate(false);
        setSelected(false);

        for (TreeItem<T> child : getChildren()) {
            if (child instanceof RRCheckBoxTreeItem<T> checkChild) {
                checkChild.clearSelection();
            }
        }
    }

    /**
     * Returns the selected-state property; changes propagate to checkbox children and update parents.
     *
     * @return the writable selected-state property
     */
    public BooleanProperty selectedProperty() {
        return selected;
    }

    /**
     * Reports this item's selected state.
     *
     * @return true if this item is selected
     */
    public boolean isSelected() {
        return selected.get();
    }

    /**
     * Sets selection, clearing indeterminate state and propagating changes through checkbox items.
     *
     * @param selected true to select this item, false to deselect it
     */
    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }

    /**
     * Returns the property representing mixed selection among checkbox-item children.
     *
     * @return the writable indeterminate-state property
     */
    public BooleanProperty indeterminateProperty() {
        return indeterminate;
    }

    /**
     * Reports whether this item represents mixed child selection.
     *
     * @return true if this item is indeterminate
     */
    public boolean isIndeterminate() {
        return indeterminate.get();
    }

    /**
     * Sets indeterminate state and updates the parent; items without selectable children cannot be indeterminate.
     *
     * @param value true to request indeterminate state, false to clear it
     */
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

    /**
     * Returns the property controlling whether a bound cell's checkbox accepts user input.
     *
     * @return the writable checkbox-disable property
     */
    public BooleanProperty disabledProperty() {
        return disabled;
    }

    /**
     * Reports whether this item's checkbox is disabled in a bound cell.
     *
     * @return true if checkbox interaction is disabled
     */
    public boolean isDisabled() {
        return disabled.get();
    }

    /**
     * Controls checkbox interaction without preventing programmatic selection changes.
     *
     * @param disabled true to disable the checkbox in a bound cell
     */
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
        } else {
            setSelectionState(false, !noneSelected);
        }

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
