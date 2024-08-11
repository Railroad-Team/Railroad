package io.github.railroad.localization.ui;

import io.github.railroad.localization.L18n;
import io.github.railroad.utility.function.FromStringFunction;
import io.github.railroad.utility.function.ToStringFunction;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.Locale;

public class LocalizedComboBox<T> extends ComboBox<T> {
    public LocalizedComboBox(ToStringFunction<T> keyFunction, FromStringFunction<T> valueOfFunction) {
        setConverter(new StringConverter<>() {
            @Override
            public String toString(T object) {
                if (object == null)
                    return "NullObject";

                String key = keyFunction.toString(object);
                if (key == null)
                    return "NullKey";

                return L18n.localize(key);
            }

            @Override
            public T fromString(String string) {
                return valueOfFunction.fromString(string.toUpperCase(Locale.ROOT));
            }
        });
    }
}