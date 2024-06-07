package io.github.railroad.ui.form;

import javafx.scene.Node;

@FunctionalInterface
public interface FormComponentValidator<T extends Node> {
    boolean validate(T node);
}
