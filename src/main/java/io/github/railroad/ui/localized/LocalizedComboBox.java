package io.github.railroad.ui.localized;

import io.github.railroad.utility.localization.L18n;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.Locale;
import java.util.function.Function;

public class LocalizedComboBox<T> extends ComboBox<T> {

    public LocalizedComboBox(Function<T, String> keyFunction, Function<String, T> valOfFunction) {
        setConverter(new StringConverter<>() {
            @Override
            public String toString(T object) {
                return L18n.localize(keyFunction.apply(object));
            }

            @Override
            public T fromString(String string) {
                return valOfFunction.apply(string.toUpperCase(Locale.ROOT));
            }
        });
    }
}
