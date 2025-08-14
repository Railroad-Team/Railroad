package dev.railroadide.core.form.ui;

import javafx.scene.control.CheckBox;

import java.util.Map;

/**
 * A form checkbox component that extends InformativeLabeledHBox to provide
 * a labeled checkbox with validation and styling support.
 */
public class FormCheckBox extends InformativeLabeledHBox<CheckBox> {
    /**
     * Constructs a new FormCheckBox with the specified label, required state, and initial selection.
     * 
     * @param label the label text for the checkbox
     * @param required whether the checkbox is required
     * @param selected the initial selected state of the checkbox
     */
    public FormCheckBox(String label, boolean required, boolean selected) {
        super(label, required, Map.of("selected", selected));
    }

    /**
     * Creates the primary checkbox component with the specified parameters.
     * 
     * @param params a map containing the parameters for the checkbox, including "selected" state
     * @return a new CheckBox instance with the specified configuration
     */
    @Override
    public CheckBox createPrimaryComponent(Map<String, Object> params) {
        var checkBox = new CheckBox();
        checkBox.setSelected(params.get("selected") != null && (boolean) params.get("selected"));
        checkBox.getStyleClass().add("rr-check-box");
        return checkBox;
    }
}
