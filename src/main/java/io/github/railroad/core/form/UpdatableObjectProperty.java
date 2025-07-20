package io.github.railroad.core.form;

import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * A property that can be updated and notifies listeners when updated.
 *
 * @param <T> The type of the property.
 */
public class UpdatableObjectProperty<T> extends SimpleObjectProperty<T> {
    private final List<Runnable> updateListeners = new ArrayList<>();

    /**
     * Creates a new UpdatableObjectProperty with a null initial value.
     */
    public UpdatableObjectProperty() {
        super();
    }

    /**
     * Creates a new UpdatableObjectProperty with the specified initial value.
     *
     * @param initialValue The initial value of the property.
     */
    public UpdatableObjectProperty(T initialValue) {
        super(initialValue);
    }

    /**
     * Updates the property and notifies listeners.
     */
    public void update() {
        this.updateListeners.forEach(Runnable::run);
    }

    /**
     * Updates the property and notifies listeners.
     *
     * @param newValue The new value of the property.
     */
    public void update(T newValue) {
        set(newValue);
        update();
    }

    /**
     * Adds a listener that will be notified when the property is updated.
     *
     * @param listener The listener to add.
     */
    public void addUpdateListener(Runnable listener) {
        this.updateListeners.add(listener);
    }

    /**
     * Removes a listener that was previously added.
     *
     * @param listener The listener to remove.
     */
    public void removeUpdateListener(Runnable listener) {
        this.updateListeners.remove(listener);
    }

    @Override
    public void set(T newValue) {
        super.set(newValue);
        update();
    }
}
