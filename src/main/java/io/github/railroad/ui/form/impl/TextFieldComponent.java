package io.github.railroad.ui.form.impl;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.form.FormComponent;
import io.github.railroad.ui.form.FormComponentChangeListener;
import io.github.railroad.ui.form.FormComponentValidator;
import io.github.railroad.ui.localized.LocalizedTextField;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class TextFieldComponent extends FormComponent<TextFieldComponent.FormTextField, TextFieldComponent.Data, TextField, String> {
    public TextFieldComponent(Data data, FormComponentValidator<TextField> validator, FormComponentChangeListener<TextField, String> listener) {
        super(data, currentData ->  new FormTextField(currentData.label, currentData.text, currentData.promptText, currentData.editable, currentData.required, currentData.translate), validator, listener);
    }

    @Override
    public ObservableValue<TextField> getValidationNode() {
        return componentProperty().map(FormTextField::getTextField);
    }

    @Override
    protected void applyListener(FormComponentChangeListener<TextField, String> listener) {
        AtomicReference<ChangeListener<String>> listenerRef = new AtomicReference<>();
        componentProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null) {
                oldValue.textField.textProperty().removeListener(listenerRef.get());
            }

            if (newValue != null) {
                listenerRef.set((observable1, oldValue1, newValue1) ->
                        listener.changed(newValue.textField, observable1, oldValue1, newValue1));

                newValue.textField.textProperty().addListener(listenerRef.get());
            }
        });
    }

    @Getter
    public static class FormTextField extends RRHBox {
        private final TextField textField;
        private final Label label;

        public FormTextField(@NotNull String label, String text, String promptText, boolean editable, boolean required, boolean translate) {
            setSpacing(10);

            this.label = createLabel(this, label, required);

            this.textField = translate ? new LocalizedTextField(promptText) : new TextField();
            this.textField.setText(text);
            if(!translate)
                this.textField.setPromptText(promptText);
            this.textField.setEditable(editable);
            getChildren().add(this.textField);
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
