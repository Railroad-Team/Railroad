package io.github.railroad.ui.form.impl;

import io.github.railroad.ui.form.FormComponent;
import io.github.railroad.ui.form.FormComponentChangeListener;
import io.github.railroad.ui.form.FormComponentValidator;
import io.github.railroad.ui.form.ui.FormTextField;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.TextField;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class TextFieldComponent extends FormComponent<FormTextField, TextFieldComponent.Data, TextField, String> {
    public TextFieldComponent(Data data, FormComponentValidator<TextField> validator, FormComponentChangeListener<TextField, String> listener, Property<TextField> bindTextFieldTo) {
        super(data, currentData ->  new FormTextField(currentData.label, currentData.required, currentData.text, currentData.promptText, currentData.editable, currentData.translate), validator, listener);
    
        if(bindTextFieldTo != null) {
            bindTextFieldTo.bind(componentProperty().map(FormTextField::getPrimaryComponent));
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
                listenerRef.set((observable1, oldValue1, newValue1) ->
                        listener.changed(newValue.getTextField(), observable1, oldValue1, newValue1));

                newValue.getTextField().textProperty().addListener(listenerRef.get());
            }
        });
    }

    public static class Builder {
        private final Data data;
        private FormComponentValidator<TextField> validator;
        private FormComponentChangeListener<TextField, String> listener;
        private Property<TextField> bindTextFieldTo;

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

        public Builder required() {
            return required(true);
        }

        public TextFieldComponent build() {
            return new TextFieldComponent(data, validator, listener, bindTextFieldTo);
        }
    }

    public static class Data {
        private final String label;
        private String text;
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
