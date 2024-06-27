package io.github.railroad.ui.form.impl;

import io.github.railroad.ui.form.FormComponent;
import io.github.railroad.ui.form.FormComponentChangeListener;
import io.github.railroad.ui.form.FormComponentValidator;
import io.github.railroad.ui.form.FormTransformer;
import io.github.railroad.ui.form.ui.FormTextField;
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

public class TextFieldComponent extends FormComponent<FormTextField, TextFieldComponent.Data, TextField, String> {
    public TextFieldComponent(Data data, FormComponentValidator<TextField> validator, FormComponentChangeListener<TextField, String> listener, Property<TextField> bindTextFieldTo, List<FormTransformer<TextField, String, ?>> transformers, EventHandler<? super KeyEvent> keyTypedHandler, @Nullable BooleanBinding visible) {
        super(data, currentData -> new FormTextField(currentData.label, currentData.required, currentData.text, currentData.promptText, currentData.editable, currentData.translate), validator, listener, transformers, visible);

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

    public static class Builder {
        private final Data data;
        private FormComponentValidator<TextField> validator;
        private FormComponentChangeListener<TextField, String> listener;
        private Property<TextField> bindTextFieldTo;
        private final List<FormTransformer<TextField, String, ?>> transformers = new ArrayList<>();
        private EventHandler<? super KeyEvent> keyTypedHandler;
        private BooleanBinding visible;

        public Builder(@NotNull String label) {
            this.data = new Data(label);
        }

        public Builder text(String text) {
            this.data.text = text;
            return this;
        }

        public Builder promptText(String promptText) {
            this.data.promptText = promptText;
            return this;
        }

        public Builder editable(boolean editable) {
            this.data.editable = editable;
            return this;
        }

        public Builder required(boolean required) {
            this.data.required = required;
            return this;
        }

        public Builder required() {
            return required(true);
        }

        public Builder translate(boolean translate) {
            this.data.translate = translate;
            return this;
        }

        public Builder validator(FormComponentValidator<TextField> validator) {
            this.validator = validator;
            return this;
        }

        public Builder listener(FormComponentChangeListener<TextField, String> listener) {
            this.listener = listener;
            return this;
        }

        public Builder bindTextFieldTo(Property<TextField> bindTextFieldTo) {
            this.bindTextFieldTo = bindTextFieldTo;
            return this;
        }

        public <W> Builder addTransformer(ObservableValue<TextField> fromComponent, Consumer<W> toComponentFunction, Function<String, W> valueMapper) {
            this.transformers.add(new FormTransformer<>(fromComponent, TextField::getText, toComponentFunction, valueMapper));
            return this;
        }

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

        public Builder keyTypedHandler(EventHandler<? super KeyEvent> keyTypedHandler) {
            this.keyTypedHandler = keyTypedHandler;
            return this;
        }

        public Builder visible(BooleanBinding visible) {
            this.visible = visible;
            return this;
        }

        public TextFieldComponent build() {
            return new TextFieldComponent(data, validator, listener, bindTextFieldTo, transformers, keyTypedHandler, visible);
        }
    }

    public static class Data {
        private final String label;
        private String text = "";
        private String promptText;
        private boolean editable = true;
        private boolean required = false;
        private boolean translate = true;

        public Data(@NotNull String label) {
            this.label = label;
        }

        public Data text(String text) {
            this.text = text;
            return this;
        }

        public Data promptText(String promptText) {
            this.promptText = promptText;
            return this;
        }

        public Data editable(boolean editable) {
            this.editable = editable;
            return this;
        }

        public Data required(boolean required) {
            this.required = required;
            return this;
        }

        public Data translate(boolean translate) {
            this.translate = translate;
            return this;
        }
    }
}
