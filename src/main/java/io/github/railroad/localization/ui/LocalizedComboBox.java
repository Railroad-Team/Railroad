package io.github.railroad.localization.ui;

import io.github.railroad.localization.L18n;
import io.github.railroad.utility.function.FromStringFunction;
import io.github.railroad.utility.function.ToStringFunction;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.Locale;
/**
 * An extension of the JavaFX ComboBox that allows for the ComboBox's items to have localised labels.
 * @param <T> The type of the ComboBox items.
 */
public class LocalizedComboBox<T> extends ComboBox<T> {
    /**
     * Creates a new LocalizedComboBox with the given key and valueOf functions.
     * @param keyFunction The function that converts the object to a key.
     * @param valueOfFunction The function that converts the key to the object.
     */
    public LocalizedComboBox(ToStringFunction<T> keyFunction, FromStringFunction<T> valueOfFunction) {
        setConverter(new StringConverter<>() {
            /**
             * Converts the object to a string that will be displayed in the ComboBox.
             * If the object is null or
             * @param object object that is in the ComboBox.
             * @return The localized string to be displayed.
             */
            @Override
            public String toString(T object) {
                if (object == null)
                    return "NullObject";

                String key = keyFunction.toString(object);
                if (key == null)
                    return "NullKey";

                return L18n.localize(key);
            }

            /**
             * Converts the ComboBox's label to an object of type T.
             * @param string The label
             * @return The object
             */
            @Override
            public T fromString(String string) {
                return valueOfFunction.fromString(string.toUpperCase(Locale.ROOT));
            }
        });
    }
}