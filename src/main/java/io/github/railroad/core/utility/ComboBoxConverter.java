package io.github.railroad.core.utility;

import javafx.util.StringConverter;
import org.jetbrains.annotations.NotNull;

/**
 * A converter for ComboBox items that allows custom string conversion.
 *
 * @param <T> the type of the items in the ComboBox
 */
public class ComboBoxConverter<T> extends StringConverter<T> {
    private final ToStringFunction<T> toStringFunction;
    private final FromStringFunction<T> fromStringFunction;

    /**
     * Constructs a new ComboBoxConverter with the specified conversion functions.
     *
     * @param toStringFunction   A function that converts an object of type T to its String representation.
     * @param fromStringFunction A function that converts a String to an object of type T.
     * @throws NullPointerException if either toStringFunction or fromStringFunction is null.
     */
    public ComboBoxConverter(@NotNull ToStringFunction<T> toStringFunction, @NotNull FromStringFunction<T> fromStringFunction) {
        this.toStringFunction = toStringFunction;
        this.fromStringFunction = fromStringFunction;
    }

    @Override
    public String toString(T object) {
        if (object == null)
            return "";

        return toStringFunction.toString(object);
    }

    @Override
    public T fromString(String string) {
        return fromStringFunction.fromString(string);
    }
}