package io.github.railroad.ui.form.ui;

import io.github.railroad.ui.localized.LocalizedTextArea;
import javafx.scene.control.TextArea;

import java.util.HashMap;
import java.util.Map;

public class FormTextArea extends InformativeLabeledHBox<TextArea> {
    public FormTextArea(String labelKey, boolean required, String text, String promptText, boolean editable, boolean resizable, boolean wrapText, boolean translate) {
        super(labelKey, required, createParams(text, promptText, editable, resizable, wrapText, translate));
    }

    private static Map<String, Object> createParams(String text, String promptText, boolean editable, boolean resizable, boolean wrapText, boolean translate) {
        Map<String, Object> params = new HashMap<>();
        if (text != null)
            params.put("text", text);
        if (promptText != null)
            params.put("promptText", promptText);
        params.put("editable", editable);
        params.put("resizable", resizable);
        params.put("wrapText", wrapText);
        params.put("translate", translate);
        return params;
    }

    @Override
    public TextArea createPrimaryComponent(Map<String, Object> params) {
        String text = (String) params.get("text");
        String promptText = (String) params.get("promptText");
        boolean editable = (boolean) params.get("editable");
        boolean resizable = (boolean) params.get("resizable");
        boolean wrapText = (boolean) params.get("wrapText");
        boolean translate = (boolean) params.get("translate");

        TextArea textArea = translate ? new LocalizedTextArea(promptText) : new TextArea();
        textArea.setText(text);
        textArea.setEditable(editable);

        if (resizable) {
            textArea.textProperty().addListener((observable, oldValue, newValue) ->
                    textArea.setMinHeight(newValue.lines().count() * 20 + 40));
        }

        textArea.setWrapText(wrapText);

        if (!translate)
            textArea.setPromptText(promptText);

        return textArea;
    }

    public TextArea getTextArea() {
        return getPrimaryComponent();
    }
}
