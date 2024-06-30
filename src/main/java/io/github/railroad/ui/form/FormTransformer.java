package io.github.railroad.ui.form;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A class that transforms a value from a component to another component.
 *
 * @param <T> The type of the component to transform from.
 * @param <V> The type of the value to transform.
 * @param <W> The type of the component to transform to.
 */
public class FormTransformer<T extends Node, V, W> {
    private final ObjectProperty<T> fromComponent = new SimpleObjectProperty<>();
    private final Function<T, V> fromComponentFunction;
    private final Consumer<W> toComponentFunction;
    private final Function<V, W> valueMapper;

    /**
     * Creates a new instance of the FormTransformer class.
     *
     * @param fromComponent         The component to transform from.
     * @param fromComponentFunction The function to get the value from the component.
     * @param toComponentFunction   The function to set the value to the component.
     * @param valueMapper           The function to map the value to the component.
     */
    public FormTransformer(@NotNull ObservableValue<T> fromComponent, @NotNull Function<T, V> fromComponentFunction, @NotNull Consumer<W> toComponentFunction, @NotNull Function<V, W> valueMapper) {
        this.fromComponent.bind(fromComponent);
        this.fromComponentFunction = fromComponentFunction;
        this.toComponentFunction = toComponentFunction;
        this.valueMapper = valueMapper;
    }

    /**
     * Transforms the value from the component to the other component.
     */
    public void transform() {
        W value = this.valueMapper.apply(this.fromComponentFunction.apply(this.fromComponent.get()));
        this.toComponentFunction.accept(value);
    }
}
