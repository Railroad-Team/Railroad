package io.github.railroad.utility;

import javafx.util.StringConverter;

public class ComboBoxConverter<T> extends StringConverter<T> {
    private final ToStringFunction<T> toStringFunction;
    private final FromStringFunction<T> fromStringFunction;

    public ComboBoxConverter(ToStringFunction<T> toStringFunction, FromStringFunction<T> fromStringFunction) {
        this.toStringFunction = toStringFunction;
        this.fromStringFunction = fromStringFunction;
    }

    @Override
    public String toString(T object) {
        return toStringFunction.toString(object);
    }

    @Override
    public T fromString(String string) {
        return fromStringFunction.fromString(string);
    }
}