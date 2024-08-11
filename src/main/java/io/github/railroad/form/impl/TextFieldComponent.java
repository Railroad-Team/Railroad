package io.github.railroad.form.impl;

import io.github.railroad.form.*;
import io.github.railroad.form.ui.FormTextField;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A form component that represents a text field.
 * Can be constructed using {@link FormComponent#textField(String, String)} or {@link TextFieldComponent.Builder}.
 *
 * @see FormComponent
 * @see FormComponent#textField(String, String)
 * @see TextFieldComponent.Builder
 */
public class TextFieldComponent extends FormComponent<FormTextField, TextFieldComponent.Data, TextField, String> {
    /**
     * Constructs a new text field component.
     *
     * @param dataKey         the key to store the data in the form data
     * @param data            the data for the text field
     * @param validator       the validator for the text field
     * @param listener        the listener for the text field
     * @param bindTextFieldTo the property to bind the text field to
     * @param transformers    the transformers for the text field
     * @param keyTypedHandler the key typed handler for the text field
     * @param visible         the visibility of the text field
     */
    public TextFieldComponent(String dataKey, Data data, FormComponentValidator<TextField> validator, FormComponentChangeListener<TextField, String> listener, Property<TextField> bindTextFieldTo, List<FormTransformer<TextField, String, ?>> transformers, EventHandler<? super KeyEvent> keyTypedHandler, @Nullable BooleanBinding visible) {
        super(dataKey, data, currentData -> new FormTextField(currentData.label, currentData.required, currentData.text, currentData.promptText, currentData.editable, currentData.translate), validator, listener, transformers, visible);

        if (bindTextFieldTo != null) {
            bindTextFieldTo.bind(componentProperty().map(FormTextField::getPrimaryComponent));
        }

        if (keyTypedHandler != null) {
            componentProperty().get().getTextField().addEventHandler(KeyEvent.KEY_TYPED, keyTypedHandler);

            componentProperty().addListener((observable, oldValue, newValue) -> {
                if (oldValue != null) {
                    oldValue.getTextField().removeEventHandler(KeyEvent.KEY_TYPED, keyTypedHandler);
                }

                if (newValue != null) {
                    newValue.getTextField().addEventHandler(KeyEvent.KEY_TYPED, keyTypedHandler);
                }
            });
        }
    }

    @Override
    public ObservableValue<TextField> getValidationNode() {
        return componentProperty().map(FormTextField::getPrimaryComponent);
    }

    @Override
    protected void applyListener(FormComponentChangeListener<TextField, String> listener) {
        AtomicReference<ChangeListener<String>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.getTextField().textProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                TextField textField = newValue.getTextField();
                listenerRef.set((observable1, oldValue1, newValue1) ->
                        listener.changed(textField, observable1, oldValue1, newValue1));

                textField.textProperty().addListener(listenerRef.get());
            }
        });
    }

    @Override
    protected void bindToFormData(FormData formData) {
        componentProperty()
                .map(FormTextField::getPrimaryComponent)
                .flatMap(TextField::textProperty)
                .addListener((observable, oldValue, newValue) ->
                        formData.addProperty(dataKey, newValue));

        formData.addProperty(dataKey, componentProperty()
                .map(FormTextField::getPrimaryComponent)
                .map(TextField::getText)
                .orElse(getData().text)
                .getValue());
    }

    @Override
    public void reset() {
        getComponent().getPrimaryComponent().setText(getData().text);
    }

    /**
     * A builder for constructing a {@link TextFieldComponent}.
     */
    public static class Builder {
        private final String dataKey;
        private final Data data;
        private final List<FormTransformer<TextField, String, ?>> transformers = new ArrayList<>();
        private FormComponentValidator<TextField> validator;
        private FormComponentChangeListener<TextField, String> listener;
        private Property<TextField> bindTextFieldTo;
        private EventHandler<? super KeyEvent> keyTypedHandler;
        private BooleanBinding visible;

        /**
         * Constructs a new builder for a text field component.
         *
         * @param dataKey the key to store the data in the form data
         * @param label   the label for the text field
         */
        public Builder(@NotNull String dataKey, @NotNull String label) {
            this.dataKey = dataKey;
            this.data = new Data(label);
        }

        /**
         * Sets the text of the text field.
         *
         * @param text the text of the text field
         * @return this builder
         */
        public Builder text(String text) {
            this.data.text = text;
            return this;
        }

        /**
         * Sets the prompt text of the text field.
         *
         * @param promptText the prompt text of the text field
         * @return this builder
         */
        public Builder promptText(String promptText) {
            this.data.promptText = promptText;
            return this;
        }

        /**
         * Sets whether the text field is editable.
         *
         * @param editable whether the text field is editable
         * @return this builder
         */
        public Builder editable(boolean editable) {
            this.data.editable = editable;
            return this;
        }

        /**
         * Sets whether the text field is required.
         *
         * @param required whether the text field is required
         * @return this builder
         */
        public Builder required(boolean required) {
            this.data.required = required;
            return this;
        }

        /**
         * Sets the text field to be required.
         * Equivalent to {@code required(true)}.
         *
         * @return this builder
         * @see #required(boolean)
         */
        public Builder required() {
            return required(true);
        }

        /**
         * Sets whether the text field should be translated.
         *
         * @param translate whether the text field should be translated
         * @return this builder
         */
        public Builder translate(boolean translate) {
            this.data.translate = translate;
            return this;
        }

        /**
         * Sets the validator for the text field.
         *
         * @param validator the validator
         * @return this builder
         */
        public Builder validator(FormComponentValidator<TextField> validator) {
            this.validator = validator;
            return this;
        }

        /**
         * Sets the listener for the text field.
         *
         * @param listener the listener
         * @return this builder
         */
        public Builder listener(FormComponentChangeListener<TextField, String> listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Binds the text field to a property.
         *
         * @param bindTextFieldTo the property to bind the text field to
         * @return this builder
         */
        public Builder bindTextFieldTo(Property<TextField> bindTextFieldTo) {
            this.bindTextFieldTo = bindTextFieldTo;
            return this;
        }

        /**
         * Adds a transformer to the text field.
         *
         * @param fromComponent       the observable value of the component to transform
         * @param toComponentFunction the function to set the value of the component
         * @param valueMapper         the function to map the value
         * @param <W>                 the type of the component
         * @return this builder
         */
        public <W> Builder addTransformer(ObservableValue<TextField> fromComponent, Consumer<W> toComponentFunction, Function<String, W> valueMapper) {
            this.transformers.add(new FormTransformer<>(fromComponent, TextField::getText, toComponentFunction, valueMapper));
            return this;
        }

        /**
         * Adds a transformer to the text field.
         *
         * @param fromComponent the observable value of the component to transform
         * @param toComponent   the component to set the value to
         * @param valueMapper   the function to map the value
         * @param <U>           the type of the component
         * @param <W>           the type of the value
         * @return this builder
         */
        public <U extends Node, W> Builder addTransformer(ObservableValue<TextField> fromComponent, ObservableValue<U> toComponent, Function<String, W> valueMapper) {
            this.transformers.add(new FormTransformer<>(fromComponent, TextField::getText, value -> {
                if (toComponent.getValue() instanceof TextField textField) {
                    textField.setText(value.toString());
                } else {
                    throw new IllegalArgumentException("Unsupported component type: " + toComponent.getValue().getClass().getName());
                }
            }, valueMapper));
            return this;
        }

        /**
         * Sets the key typed handler for the text field.
         *
         * @param keyTypedHandler the key typed handler
         * @return this builder
         */
        public Builder keyTypedHandler(EventHandler<? super KeyEvent> keyTypedHandler) {
            this.keyTypedHandler = keyTypedHandler;
            return this;
        }

        /**
         * Sets the visibility of the text field.
         *
         * @param visible the visibility
         * @return this builder
         */
        public Builder visible(BooleanBinding visible) {
            this.visible = visible;
            return this;
        }

        /**
         * Builds the text field component.
         *
         * @return the text field component
         */
        public TextFieldComponent build() {
            return new TextFieldComponent(dataKey, data, validator, listener, bindTextFieldTo, transformers, keyTypedHandler, visible);
        }
    }

    /**
     * The data for the text field.
     */
    public static class Data {
        private final String label;
        private String text = "";
        private String promptText;
        private boolean editable = true;
        private boolean required = false;
        private boolean translate = true;

        /**
         * Constructs a new data for the text field.
         *
         * @param label the label for the text field
         */
        public Data(@NotNull String label) {
            this.label = label;
        }

        /**
         * Sets the text of the text field.
         *
         * @param text the text of the text field
         * @return this builder
         */
        public Data text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Sets the prompt text of the text field.
         *
         * @param promptText the prompt text of the text field
         * @return this builder
         */
        public Data promptText(String promptText) {
            this.promptText = promptText;
            return this;
        }

        /**
         * Sets whether the text field is editable.
         *
         * @param editable whether the text field is editable
         * @return this builder
         */
        public Data editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        /**
         * Sets whether the text field is required.
         *
         * @param required whether the text field is required
         * @return this builder
         */
        public Data required(boolean required) {
            this.required = required;
            return this;
        }

        /**
         * Sets whether the text field should be translated.
         *
         * @param translate whether the text field should be translated
         * @return this builder
         */
        public Data translate(boolean translate) {
            this.translate = translate;
            return this;
        }
    }
}
