package io.github.railroad.utility;

@FunctionalInterface
public interface FromStringFunction<T> {
    T fromString(String string);
}