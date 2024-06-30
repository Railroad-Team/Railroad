package io.github.railroad.form.ui;

import javafx.scene.control.CheckBox;

import java.util.Map;

public class FormCheckBox extends InformativeLabeledHBox<CheckBox> {
    public FormCheckBox(String label, boolean required, boolean selected) {
        super(label, required, Map.of("selected", selected));
    }

    @Override
    public CheckBox createPrimaryComponent(Map<String, Object> params) {
        var checkBox = new CheckBox();
        checkBox.setSelected(params.get("selected") != null && (boolean) params.get("selected"));
        return checkBox;
    }
}
