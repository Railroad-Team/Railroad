package io.github.railroad.utility;

@FunctionalInterface
public interface ToStringFunction<T> {
    String toString(T object);
}
