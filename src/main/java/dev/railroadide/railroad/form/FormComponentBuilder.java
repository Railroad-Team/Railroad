package dev.railroadide.railroad.form;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Defines the common configuration contract for form component builders.
 *
 * @param <C> the component type produced by the builder
 * @param <V> the JavaFX node type used for validation and change handling
 * @param <W> the value type observed by change listeners
 * @param <B> the concrete builder type returned by fluent methods
 */
public interface FormComponentBuilder<C extends FormComponent<?, ?, ?, ?>, V extends Node, W, B extends FormComponentBuilder<C, V, W, B>> {
    /**
     * Returns the key under which this component stores its value in form data.
     *
     * @return the component's form-data key
     */
    String dataKey();

    /**
     * Sets the validator used for this component.
     *
     * @param validator the component validator
     * @return this builder
     */
    B validator(FormComponentValidator<V> validator);

    /**
     * Sets the listener notified when the component value changes.
     *
     * @param listener the component change listener
     * @return this builder
     */
    B listener(FormComponentChangeListener<V, W> listener);

    /**
     * Adds a transformer that maps the value from this component to a target
     * value and passes it to a consumer.
     *
     * @param fromComponent the observable containing the source component
     * @param toComponentFunction the consumer that receives the transformed value
     * @param valueMapper the function that maps the source value to the target value
     * @param <X> the target value type
     * @return this builder
     */
    <X> B addTransformer(ObservableValue<V> fromComponent, Consumer<X> toComponentFunction, Function<W, X> valueMapper);

    /**
     * Adds a transformer that maps the value from this component to another
     * form component.
     *
     * @param fromComponent the observable containing the source component
     * @param toComponent the observable containing the target component
     * @param valueMapper the function that maps the source value to the target value
     * @param <U> the target component node type
     * @param <X> the target value type
     * @return this builder
     */
    <U extends Node, X> B addTransformer(
        ObservableValue<V> fromComponent,
        ObservableValue<U> toComponent,
        Function<W, X> valueMapper
    );

    /**
     * Sets the binding controlling the component's visibility.
     *
     * @param visible the visibility binding
     * @return this builder
     */
    B visible(BooleanBinding visible);

    /**
     * Builds the configured form component.
     *
     * @return the configured component
     */
    C build();
}
