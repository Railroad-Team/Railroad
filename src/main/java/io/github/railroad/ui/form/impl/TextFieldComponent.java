package io.github.railroad.ui.form.impl;

import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.form.FormComponent;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class TextFieldComponent extends FormComponent<TextFieldComponent.FormTextField, TextFieldComponent.Data> {
    public TextFieldComponent(Supplier<Data> dataSupplier) {
        super(dataSupplier, data ->  new FormTextField(data.label, data.text, data.promptText, data.editable, data.required));
    }

    public static class FormTextField extends RRHBox {
        private final TextField textField;
        private final Label label;

        public FormTextField(@NotNull String label, String text, String promptText, boolean editable, boolean required) {
            setSpacing(10);

            this.label = new Label(label);
            this.textField = new TextField(text);
            this.textField.setPromptText(promptText);
            this.textField.setEditable(editable);
            getChildren().addAll(this.label, this.textField);
            if (required) {
                getChildren().add(createAsterisk());
            }
        }

        public TextField getTextField() {
            return textField;
        }

        public Label getLabel() {
            return label;
        }
    }

    public static class Data {
        private final String label;
        private String text;
        private String promptText;
        private boolean editable = true;
        private boolean required = false;

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
    }
}
