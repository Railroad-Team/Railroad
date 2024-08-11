package io.github.railroad.utility.function;

@FunctionalInterface
public interface FromStringFunction<T> {
    T fromString(String string);
}