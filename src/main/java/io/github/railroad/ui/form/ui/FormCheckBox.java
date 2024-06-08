package io.github.railroad.ui.form.ui;

import javafx.scene.control.CheckBox;

public class FormCheckBox extends InformativeLabeledHBox<CheckBox> {
    private final boolean selected;

    public FormCheckBox(String label, boolean required, boolean selected) {
        super(label, required);
        this.selected = selected;
    }

    @Override
    public CheckBox createPrimaryComponent() {
        var checkBox = new CheckBox();
        checkBox.setSelected(selected);
        return checkBox;
    }
}
