package dev.railroadide.core.form;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Standardises configuration contracts for form component builders.
 */
public interface FormComponentBuilder<C extends FormComponent<?, ?, ?, ?>, V extends Node, W, B extends FormComponentBuilder<C, V, W, B>> {
    B validator(FormComponentValidator<V> validator);

    B listener(FormComponentChangeListener<V, W> listener);

    <X> B addTransformer(ObservableValue<V> fromComponent, Consumer<X> toComponentFunction, Function<W, X> valueMapper);

    <U extends Node, X> B addTransformer(ObservableValue<V> fromComponent, ObservableValue<U> toComponent, Function<W, X> valueMapper);

    B visible(BooleanBinding visible);

    C build();
}

