package dev.railroadide.core.form;

import dev.railroadide.core.utility.ServiceLocator;
import dev.railroadide.logger.Logger;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;
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
    private final Function<V, CompletableFuture<W>> futureMapper;
    private final boolean asynchronous;

    /**
     * Creates a new instance of the FormTransformer class.
     *
     * @param fromComponent         The component to transform from.
     * @param fromComponentFunction The function to get the value from the component.
     * @param toComponentFunction   The function to set the value to the component.
     * @param valueMapper           The function to map the value to the component.
     */
    public FormTransformer(@NotNull ObservableValue<T> fromComponent, @NotNull Function<T, V> fromComponentFunction, @NotNull Consumer<W> toComponentFunction, @NotNull Function<V, W> valueMapper) {
        this(fromComponent, fromComponentFunction, toComponentFunction,
            value -> CompletableFuture.completedFuture(valueMapper.apply(value)),
            false);
    }

    public static <T extends Node, V, W> FormTransformer<T, V, W> async(@NotNull ObservableValue<T> fromComponent,
                                                                        @NotNull Function<T, V> fromComponentFunction,
                                                                        @NotNull Consumer<W> toComponentFunction,
                                                                        @NotNull Function<V, CompletableFuture<W>> futureMapper) {
        return new FormTransformer<>(fromComponent, fromComponentFunction, toComponentFunction, futureMapper, true);
    }

    private FormTransformer(@NotNull ObservableValue<T> fromComponent,
                            @NotNull Function<T, V> fromComponentFunction,
                            @NotNull Consumer<W> toComponentFunction,
                            @NotNull Function<V, CompletableFuture<W>> futureMapper,
                            boolean asynchronous) {
        this.fromComponent.bind(fromComponent);
        this.fromComponentFunction = fromComponentFunction;
        this.toComponentFunction = toComponentFunction;
        this.futureMapper = futureMapper;
        this.asynchronous = asynchronous;
    }

    /**
     * Transforms the value from the component to the other component synchronously.
     */
    public void transformSync() {
        if (asynchronous) {
            ServiceLocator.getService(Logger.class).warn("FormTransformer#transformSync called on asynchronous transformer; falling back to async execution");
            transform();
            return;
        }

        CompletableFuture<W> future = this.futureMapper.apply(this.fromComponentFunction.apply(this.fromComponent.get()));
        if (future == null)
            return;

        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                ServiceLocator.getService(Logger.class).error("Failed to transform form component value synchronously", throwable);
                return;
            }

            this.toComponentFunction.accept(result);
        });
    }

    /**
     * Transforms the value from the component to the other component asynchronously.
     */
    public void transform() {
        T component = this.fromComponent.get();
        if (component == null)
            return;

        V value;
        try {
            value = this.fromComponentFunction.apply(component);
        } catch (Exception exception) {
            ServiceLocator.getService(Logger.class).error("Failed to read value from form component", exception);
            return;
        }

        CompletableFuture<W> future;
        try {
            future = this.futureMapper.apply(value);
        } catch (Exception exception) {
            ServiceLocator.getService(Logger.class).error("Failed to start asynchronous form transformation", exception);
            return;
        }

        if (future == null)
            return;

        future.whenComplete((result, throwable) -> {
            if (throwable != null) {
                ServiceLocator.getService(Logger.class).error("Form transformation failed", throwable);
                return;
            }

            Platform.runLater(() -> this.toComponentFunction.accept(result));
        });
    }
}
