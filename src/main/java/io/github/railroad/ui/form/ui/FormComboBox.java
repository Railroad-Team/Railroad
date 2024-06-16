package io.github.railroad.ui.form.ui;

import io.github.railroad.ui.localized.LocalizedComboBox;
import io.github.railroad.utility.FromStringFunction;
import io.github.railroad.utility.ToStringFunction;
import javafx.scene.control.ComboBox;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class FormComboBox<T> extends InformativeLabeledHBox<ComboBox<T>> {
    public FormComboBox(String labelKey, boolean required, List<T> items, boolean editable, boolean translate, ToStringFunction<T> keyFunction, FromStringFunction<T> valueOfFunction) {
        super(labelKey, required, Map.of("items", items, "editable", editable, "translate", translate, "keyFunction", keyFunction, "valueOfFunction", valueOfFunction));
    }

    @SuppressWarnings("unchecked")
    @Override
    public ComboBox<T> createPrimaryComponent(Map<String, Object> params) {
        List<T> items = (List<T>) params.get("items");
        boolean editable = (boolean) params.get("editable");
        boolean translate = (boolean) params.get("translate");
        ToStringFunction<T> keyFunction = (ToStringFunction<T>) params.get("keyFunction");
        FromStringFunction<T> valueOfFunction = (FromStringFunction<T>) params.get("valueOfFunction");

        ComboBox<T> comboBox = translate ? new LocalizedComboBox<>(keyFunction, valueOfFunction) : new ComboBox<>();
        comboBox.getItems().addAll(items);
        comboBox.setEditable(editable);

        return comboBox;
    }
}
