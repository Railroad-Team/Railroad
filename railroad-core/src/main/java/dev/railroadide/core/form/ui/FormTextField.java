package dev.railroadide.core.form.ui;

import dev.railroadide.core.form.HasSetValue;
import dev.railroadide.core.ui.RRTextField;
import javafx.application.Platform;
import javafx.scene.control.TextField;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A form text field component that extends InformativeLabeledHBox to provide
 * a labeled text field with validation, localization, and styling support.
 */
public class FormTextField extends InformativeLabeledHBox<TextField> implements HasSetValue {
    /**
     * Constructs a new FormTextField with the specified configuration.
     *
     * @param labelKey   the localization key for the label text
     * @param required   whether the text field is required
     * @param promptText the placeholder text to display when empty
     * @param editable   whether the text field is editable
     * @param translate  whether to use localization for the prompt text
     */
    public FormTextField(String labelKey, boolean required, String promptText, boolean editable, boolean translate) {
        super(labelKey, required, createParams(promptText, editable, translate));
    }

    /**
     * Creates the parameters map for the text field component.
     *
     * @param promptText the placeholder text
     * @param editable   whether the text field is editable
     * @param translate  whether to use localization
     * @return a map containing the component parameters
     */
    private static Map<String, Object> createParams(String promptText, boolean editable, boolean translate) {
        Map<String, Object> params = new HashMap<>();
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
    @Override
    public TextField createPrimaryComponent(Map<String, Object> params) {
        String promptText = (String) params.get("promptText");
        boolean editable = (boolean) params.get("editable");
        boolean translate = (boolean) params.get("translate");

        RRTextField textField = translate ? new RRTextField(promptText) : new RRTextField();
        textField.setEditable(editable);
        textField.getStyleClass().add("rr-text-field");
        if (!translate)
            textField.setPromptText(promptText);

        return textField;
    }

    @Override
    public void setValue(Object value) {
        Platform.runLater(() -> getPrimaryComponent().setText(Objects.toString(value, "")));
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
