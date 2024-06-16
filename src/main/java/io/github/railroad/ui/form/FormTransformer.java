package io.github.railroad.ui.form;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;

public class FormTransformer<T extends Node, V, W> {
    private final ObjectProperty<T> fromComponent = new SimpleObjectProperty<>();
    private final Function<T, V> fromComponentFunction;
    private final Consumer<W> toComponentFunction;
    private final Function<V, W> valueMapper;

    public FormTransformer(@NotNull ObservableValue<T> fromComponent, @NotNull Function<T, V> fromComponentFunction, @NotNull Consumer<W> toComponentFunction, @NotNull Function<V, W> valueMapper) {
        this.fromComponent.bind(fromComponent);
        this.fromComponentFunction = fromComponentFunction;
        this.toComponentFunction = toComponentFunction;
        this.valueMapper = valueMapper;
    }

    public void transform() {
        W value = this.valueMapper.apply(this.fromComponentFunction.apply(this.fromComponent.get()));
        this.toComponentFunction.accept(value);
    }
}
