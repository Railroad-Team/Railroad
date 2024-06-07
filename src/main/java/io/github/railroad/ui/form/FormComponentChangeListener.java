package io.github.railroad.ui.form;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

@FunctionalInterface
public interface FormComponentChangeListener<T extends Node, U> {
    void changed(T node, ObservableValue<? extends U> observable, U oldValue, U newValue);
}
