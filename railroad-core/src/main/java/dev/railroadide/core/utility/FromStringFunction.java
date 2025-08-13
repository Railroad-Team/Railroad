package dev.railroadide.core.utility;

/**
 * A functional interface that defines a method to convert a String to an object of type T.
 * This is typically used for parsing or converting string representations of objects.
 *
 * @param <T> the type of object to be created from the string
 */
@FunctionalInterface
public interface FromStringFunction<T> {
    /**
     * Converts a given string to an object of type T.
     *
     * @param string the string to convert
     * @return an object of type T created from the string
     * @throws IllegalArgumentException if the string cannot be converted to an object of type T
     */
    T fromString(String string);
}