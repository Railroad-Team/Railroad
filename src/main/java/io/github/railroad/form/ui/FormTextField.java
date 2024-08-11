package io.github.railroad.form.ui;

import io.github.railroad.localization.ui.LocalizedTextField;
import javafx.scene.control.TextField;

import java.util.HashMap;
import java.util.Map;

public class FormTextField extends InformativeLabeledHBox<TextField> {
    public FormTextField(String labelKey, boolean required, String text, String promptText, boolean editable, boolean translate) {
        super(labelKey, required, createParams(text, promptText, editable, translate));
    }

    private static Map<String, Object> createParams(String text, String promptText, boolean editable, boolean translate) {
        Map<String, Object> params = new HashMap<>();
        if (text != null)
            params.put("text", text);
        if (promptText != null)
            params.put("promptText", promptText);
        params.put("editable", editable);
        params.put("translate", translate);
        return params;
    }

    @Override
    public TextField createPrimaryComponent(Map<String, Object> params) {
        String text = (String) params.get("text");
        String promptText = (String) params.get("promptText");
        boolean editable = (boolean) params.get("editable");
        boolean translate = (boolean) params.get("translate");

        TextField textField = translate ? new LocalizedTextField(promptText) : new TextField();
        textField.setText(text);
        textField.setEditable(editable);

        if (!translate)
            textField.setPromptText(promptText);

        return textField;
    }

    public TextField getTextField() {
        return getPrimaryComponent();
    }
}
