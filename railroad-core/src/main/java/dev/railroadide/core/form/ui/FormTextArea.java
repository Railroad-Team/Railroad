package dev.railroadide.core.form.ui;

import dev.railroadide.core.form.HasSetValue;
import dev.railroadide.core.ui.localized.LocalizedTextArea;
import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A form text area component that extends InformativeLabeledHBox to provide
 * a labeled text area with validation, localization, and styling support.
 * Supports auto-resizing and text wrapping features.
 */
public class FormTextArea extends InformativeLabeledHBox<TextArea> implements HasSetValue {
    /**
     * Constructs a new FormTextArea with the specified configuration.
     *
     * @param labelKey the localization key for the label text
     * @param required whether the text area is required
     * @param text the initial text content
     * @param promptText the placeholder text to display when empty
     * @param editable whether the text area is editable
     * @param resizable whether the text area should auto-resize based on content
     * @param wrapText whether text should wrap to new lines
     * @param translate whether to use localization for the prompt text
     */
    public FormTextArea(String labelKey, boolean required, String text, String promptText, boolean editable, boolean resizable, boolean wrapText, boolean translate) {
        super(labelKey, required, createParams(text, promptText, editable, resizable, wrapText, translate));
    }

    /**
     * Creates the parameters map for the text area component.
     *
     * @param text the initial text content
     * @param promptText the placeholder text
     * @param editable whether the text area is editable
     * @param resizable whether the text area should auto-resize
     * @param wrapText whether text should wrap
     * @param translate whether to use localization
     * @return a map containing the component parameters
     */
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

    /**
     * Creates the primary text area component with the specified parameters.
     *
     * @param params a map containing the parameters for the text area
     * @return a new TextArea instance with the specified configuration
     */
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
        textArea.getStyleClass().add("rr-text-area");
        if (resizable) {
            textArea.textProperty().addListener((observable, oldValue, newValue) ->
                    textArea.setMinHeight(newValue.lines().count() * 20 + 40));
        }
        textArea.setWrapText(wrapText);
        if (!translate)
            textArea.setPromptText(promptText);
        return textArea;
    }

    /**
     * Gets the underlying text area component.
     *
     * @return the text area component
     */
    public TextArea getTextArea() {
        return getPrimaryComponent();
    }

    @Override
    public void setValue(Object value) {
        Platform.runLater(() -> getPrimaryComponent().setText(Objects.toString(value, "")));
    }
}
