package io.github.railroad.ui.form;

import javafx.scene.Node;

import java.util.function.Function;

@FunctionalInterface
public interface FormComponentValidator<T extends Node> extends Function<T, ValidationResult> {}
