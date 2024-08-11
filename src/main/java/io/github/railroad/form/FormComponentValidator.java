package io.github.railroad.form;

import javafx.scene.Node;

import java.util.function.Function;

/**
 * A functional interface for validating a form component.
 *
 * @param <T> the type of the form component
 */
@FunctionalInterface
public interface FormComponentValidator<T extends Node> extends Function<T, ValidationResult> {
}
