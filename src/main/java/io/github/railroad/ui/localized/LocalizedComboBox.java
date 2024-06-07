package io.github.railroad.ui.localized;

import io.github.railroad.utility.FromStringFunction;
import io.github.railroad.utility.ToStringFunction;
import io.github.railroad.utility.localization.L18n;
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