package io.github.railroad.form.ui;

import io.github.railroad.localization.ui.LocalizedComboBox;
import io.github.railroad.utility.function.FromStringFunction;
import io.github.railroad.utility.function.ToStringFunction;
import javafx.scene.control.ComboBox;
import lombok.Getter;
import org.apache.groovy.util.Maps;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

@Getter
public class FormComboBox<T> extends InformativeLabeledHBox<ComboBox<T>> {
    public FormComboBox(String labelKey, boolean required, List<T> items, boolean editable, boolean translate, @Nullable ToStringFunction<T> keyFunction, @Nullable FromStringFunction<T> valueOfFunction) {
        super(labelKey, required, Maps.of("items", items, "editable", editable, "translate", translate, "keyFunction", keyFunction, "valueOfFunction", valueOfFunction));
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
        comboBox.getStyleClass().add("rr-combo-box");
        return comboBox;
    }
}
