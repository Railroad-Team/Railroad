package dev.railroadide.core.form.impl;

import dev.railroadide.core.form.*;
import dev.railroadide.core.form.*;
import dev.railroadide.core.form.ui.FormCheckBox;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A form component that represents a checkbox.
 * Can be constructed using {@link FormComponent#checkBox(String, String)} or {@link Builder}.
 *
 * @see FormComponent
 * @see FormComponent#checkBox(String, String)
 * @see Builder
 */
public class CheckBoxComponent extends FormComponent<FormCheckBox, CheckBoxComponent.Data, CheckBox, Boolean> {
    /**
     * Constructs a new checkbox component.
     *
     * @param dataKey        the key to store the data in the form data
     * @param data           the data for the checkbox
     * @param validator      the validator for the checkbox
     * @param listener       the listener for the checkbox
     * @param bindCheckboxTo the property to bind the checkbox to
     * @param transformers   the transformers for the checkbox
     * @param visible        the visibility of the checkbox
     */
    public CheckBoxComponent(String dataKey, Data data, FormComponentValidator<CheckBox> validator, FormComponentChangeListener<CheckBox, Boolean> listener, Property<CheckBox> bindCheckboxTo, List<FormTransformer<CheckBox, Boolean, ?>> transformers, @Nullable BooleanBinding visible) {
        super(dataKey, data, dataCurrent -> new FormCheckBox(dataCurrent.label, dataCurrent.required, dataCurrent.selected), validator, listener, transformers, visible);

        if (bindCheckboxTo != null) {
            bindCheckboxTo.bind(componentProperty().map(FormCheckBox::getPrimaryComponent));
        }
    }

    @Override
    public ObservableValue<CheckBox> getValidationNode() {
        return componentProperty().map(FormCheckBox::getPrimaryComponent);
    }

    @Override
    protected void applyListener(FormComponentChangeListener<CheckBox, Boolean> listener) {
        AtomicReference<ChangeListener<Boolean>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.getPrimaryComponent().selectedProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                listenerRef.set((observable1, oldValue1, newValue1) ->
                        listener.changed(newValue.getPrimaryComponent(), observable1, oldValue1, newValue1));

                newValue.getPrimaryComponent().selectedProperty().addListener(listenerRef.get());
            }
        });
    }

    @Override
    protected void bindToFormData(FormData formData) {
        componentProperty()
                .map(FormCheckBox::getPrimaryComponent)
                .flatMap(CheckBox::selectedProperty)
                .addListener((observable, oldValue, newValue) ->
                        formData.addProperty(dataKey, newValue));

        formData.addProperty(dataKey, componentProperty()
                .map(FormCheckBox::getPrimaryComponent)
                .map(CheckBox::isSelected)
                .orElse(getData().selected)
                .getValue());
    }

    @Override
    public void reset() {
        getComponent().getPrimaryComponent().setSelected(getData().selected);
    }

    /**
     * A builder for constructing a {@link CheckBoxComponent}.
     */
    public static class Builder {
        private final String dataKey;
        private final Data data;
        private final List<FormTransformer<CheckBox, Boolean, ?>> transformers = new ArrayList<>();
        private FormComponentValidator<CheckBox> validator;
        private FormComponentChangeListener<CheckBox, Boolean> listener;
        private Property<CheckBox> bindCheckBoxTo;
        private BooleanBinding visible;

        /**
         * Constructs a new builder for a checkbox component.
         *
         * @param dataKey the key to store the data in the form data
         * @param label   the label for the checkbox
         */
        public Builder(@NotNull String dataKey, @NotNull String label) {
            this.dataKey = dataKey;
            this.data = new Data(label);
        }

        /**
         * Sets the selected state of the checkbox.
         *
         * @param selected the selected state
         * @return this builder
         */
        public Builder selected(boolean selected) {
            this.data.selected(selected);
            return this;
        }

        /**
         * Sets whether the checkbox is required.
         *
         * @param required whether the checkbox is required
         * @return this builder
         */
        public Builder required(boolean required) {
            this.data.required(required);
            return this;
        }

        /**
         * Sets the validator for the checkbox.
         *
         * @param validator the validator
         * @return this builder
         */
        public Builder validator(FormComponentValidator<CheckBox> validator) {
            this.validator = validator;
            return this;
        }

        /**
         * Sets the listener for the checkbox.
         *
         * @param listener the listener
         * @return this builder
         */
        public Builder listener(FormComponentChangeListener<CheckBox, Boolean> listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Binds the checkbox to a property.
         *
         * @param bindCheckBoxTo the property to bind the checkbox to
         * @return this builder
         */
        public Builder bindCheckBoxTo(Property<CheckBox> bindCheckBoxTo) {
            this.bindCheckBoxTo = bindCheckBoxTo;
            return this;
        }

        /**
         * Adds a transformer to the checkbox.
         *
         * @param fromComponent       the observable value to get the value from
         * @param toComponentFunction the function to set the value to the component
         * @param valueMapper         the function to map the value
         * @return this builder
         * @type W - the type of the value
         */
        public <W> Builder addTransformer(ObservableValue<CheckBox> fromComponent, Consumer<W> toComponentFunction, Function<Boolean, W> valueMapper) {
            transformers.add(new FormTransformer<>(fromComponent, CheckBox::isSelected, toComponentFunction, valueMapper));
            return this;
        }

        /**
         * Adds a transformer to the checkbox.
         *
         * @param fromComponent the observable value to get the value from
         * @param toComponent   the observable value to set the value to
         * @param valueMapper   the function to map the value
         * @return this builder
         * @type U - the type of the component
         * @type W - the type of the value
         */
        public <U extends Node, W> Builder addTransformer(ObservableValue<CheckBox> fromComponent, ObservableValue<U> toComponent, Function<Boolean, W> valueMapper) {
            this.transformers.add(new FormTransformer<>(fromComponent, CheckBox::isSelected, value -> {
                if (toComponent.getValue() instanceof TextField textField) {
                    textField.setText(value.toString());
                } else if (toComponent.getValue() instanceof CheckBox checkBox) {
                    try {
                        checkBox.setSelected((Boolean) value);
                    } catch (Exception ignored) {
                    }
                } else {
                    throw new IllegalArgumentException("Unsupported component type: " + toComponent.getValue().getClass().getName());
                }
            }, valueMapper));
            return this;
        }

        /**
         * Sets the visibility of the checkbox.
         *
         * @param visible the visibility
         * @return this builder
         */
        public Builder visible(BooleanBinding visible) {
            this.visible = visible;
            return this;
        }

        /**
         * Builds the checkbox component.
         *
         * @return the checkbox component
         */
        public CheckBoxComponent build() {
            return new CheckBoxComponent(dataKey, data, validator, listener, bindCheckBoxTo, transformers, visible);
        }
    }

    /**
     * The data for the checkbox.
     */
    public static class Data {
        private final String label;
        private boolean selected = false;
        private boolean required = false;

        /**
         * Constructs a new data for the checkbox.
         *
         * @param label the label for the checkbox
         */
        public Data(@NotNull String label) {
            this.label = label;
        }

        /**
         * Gets the label for the checkbox.
         *
         * @return the label
         */
        public Data selected(boolean selected) {
            this.selected = selected;
            return this;
        }

        /**
         * Gets whether the checkbox is required.
         *
         * @return whether the checkbox is required
         */
        public Data required(boolean required) {
            this.required = required;
            return this;
        }
    }
}
