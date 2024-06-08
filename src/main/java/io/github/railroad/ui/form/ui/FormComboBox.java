package io.github.railroad.ui.form.ui;

import io.github.railroad.ui.localized.LocalizedComboBox;
import io.github.railroad.utility.FromStringFunction;
import io.github.railroad.utility.ToStringFunction;
import javafx.scene.control.ComboBox;
import lombok.Getter;

import java.util.List;

@Getter
public class FormComboBox<T> extends InformativeLabeledHBox<ComboBox<T>> {
    private final List<T> items;
    private final boolean editable;
    private final boolean translate;
    private final ToStringFunction<T> keyFunction;
    private final FromStringFunction<T> valueOfFunction;

    public FormComboBox(String labelKey, boolean required, List<T> items, boolean editable, boolean translate, ToStringFunction<T> keyFunction, FromStringFunction<T> valueOfFunction) {
        super(labelKey, required);
        this.items = items;
        this.editable = editable;
        this.translate = translate;
        this.keyFunction = keyFunction;
        this.valueOfFunction = valueOfFunction;
    }

    @Override
    public ComboBox<T> createPrimaryComponent() {
        ComboBox<T> comboBox = this.translate ? new LocalizedComboBox<>(this.keyFunction, this.valueOfFunction) : new ComboBox<>();
        comboBox.getItems().addAll(this.items);
        comboBox.setEditable(this.editable);

        return comboBox;
    }
}
