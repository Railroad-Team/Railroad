package dev.railroadide.core.form.ui;

import dev.railroadide.core.ui.RRTextField;
import javafx.scene.control.TextField;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A form text field component that extends InformativeLabeledHBox to provide
 * a labeled text field with validation, localization, and styling support.
 */
public class FormTextField extends InformativeLabeledHBox<TextField> {
    /**
     * Constructs a new FormTextField with the specified configuration.
     *
     * @param labelKey the localization key for the label text
     * @param required whether the text field is required
     * @param text the initial text content
     * @param promptText the placeholder text to display when empty
     * @param editable whether the text field is editable
     * @param translate whether to use localization for the prompt text
     */
    public FormTextField(String labelKey, boolean required, Supplier<String> text, String promptText, boolean editable, boolean translate) {
        super(labelKey, required, createParams(text, promptText, editable, translate));
    }

    /**
     * Creates the parameters map for the text field component.
     *
     * @param text the initial text content
     * @param promptText the placeholder text
     * @param editable whether the text field is editable
     * @param translate whether to use localization
     * @return a map containing the component parameters
     */
    private static Map<String, Object> createParams(Supplier<String> text, String promptText, boolean editable, boolean translate) {
        Map<String, Object> params = new HashMap<>();
        if (text != null)
            params.put("text", text);
        if (promptText != null)
            params.put("promptText", promptText);
        params.put("editable", editable);
        params.put("translate", translate);
        return params;
    }

    /**
     * Creates the primary text field component with the specified parameters.
     *
     * @param params a map containing the parameters for the text field
     * @return a new TextField instance with the specified configuration
     */
    @SuppressWarnings("unchecked")
    @Override
    public TextField createPrimaryComponent(Map<String, Object> params) {
        String text = ((Supplier<String>) params.get("text")).get();
        String promptText = (String) params.get("promptText");
        boolean editable = (boolean) params.get("editable");
        boolean translate = (boolean) params.get("translate");

        RRTextField textField = translate ? new RRTextField(promptText) : new RRTextField();
        textField.setText(text);
        textField.setEditable(editable);
        textField.getStyleClass().add("rr-text-field");
        if (!translate)
            textField.setPromptText(promptText);

        return textField;
    }

    /**
     * Gets the underlying text field component.
     *
     * @return the text field component
     */
    public TextField getTextField() {
        return getPrimaryComponent();
    }
}
