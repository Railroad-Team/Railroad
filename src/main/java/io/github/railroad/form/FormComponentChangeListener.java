package io.github.railroad.form;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

/**
 * A functional interface for handling changes in form components.
 *
 * @param <T> The type of the form component.
 * @param <U> The type of the value of the form component.
 */
@FunctionalInterface
public interface FormComponentChangeListener<T extends Node, U> {
    /**
     * Handles the change in the form component.
     *
     * @param node       The form component.
     * @param observable The observable value of the form component.
     * @param oldValue   The old value of the form component.
     * @param newValue   The new value of the form component.
     */
    void changed(T node, ObservableValue<? extends U> observable, U oldValue, U newValue);
}
