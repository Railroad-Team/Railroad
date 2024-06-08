package io.github.railroad.ui.form.ui;

import io.github.railroad.ui.localized.LocalizedTextField;
import javafx.scene.control.TextField;

public class FormTextField extends InformativeLabeledHBox<TextField> {
    private final String text, promptText;
    private final boolean editable, translate;

    public FormTextField(String labelKey, boolean required, String text, String promptText, boolean editable, boolean translate) {
        super(labelKey, required);
        this.text = text;
        this.promptText = promptText;
        this.editable = editable;
        this.translate = translate;
    }

    @Override
    public TextField createPrimaryComponent() {
        TextField textField = translate ? new LocalizedTextField(promptText) : new TextField();
        textField.setText(text);
        textField.setEditable(editable);

        if(!translate)
            textField.setPromptText(promptText);

        return textField;
    }

    public TextField getTextField() {
        return getPrimaryComponent();
    }
}
