package io.github.railroad.utility.function;

@FunctionalInterface
public interface ToStringFunction<T> {
    String toString(T object);
}
