package io.github.railroad.core.utility;

/**
 * A functional interface for converting an object of type T to its String representation.
 *
 * @param <T> The type of the object to be converted to a String.
 */
@FunctionalInterface
public interface ToStringFunction<T> {

    /**
     * Converts the given object to its String representation.
     *
     * @param object The object to convert to a String.
     * @return The String representation of the given object.
     */
    String toString(T object);
}