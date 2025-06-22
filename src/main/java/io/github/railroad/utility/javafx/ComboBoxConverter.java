package io.github.railroad.utility.javafx;

import io.github.railroad.utility.function.FromStringFunction;
import io.github.railroad.utility.function.ToStringFunction;
import javafx.util.StringConverter;
import org.jetbrains.annotations.NotNull;

public class ComboBoxConverter<T> extends StringConverter<T> {
    private final ToStringFunction<T> toStringFunction;
    private final FromStringFunction<T> fromStringFunction;

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