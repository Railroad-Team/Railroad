package io.github.railroad.ui.form;

import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
import java.util.List;

public class UpdatableObjectProperty<T> extends SimpleObjectProperty<T> {
    private final List<Runnable> updateListeners = new ArrayList<>();

    public UpdatableObjectProperty() {
        super();
    }

    public UpdatableObjectProperty(T initialValue) {
        super(initialValue);
    }

    public void update() {
        this.updateListeners.forEach(Runnable::run);
    }

    public void update(T newValue) {
        set(newValue);
        update();
    }

    public void addUpdateListener(Runnable listener) {
        this.updateListeners.add(listener);
    }

    public void removeUpdateListener(Runnable listener) {
        this.updateListeners.remove(listener);
    }

    @Override
    public void set(T newValue) {
        super.set(newValue);
        update();
    }
}
