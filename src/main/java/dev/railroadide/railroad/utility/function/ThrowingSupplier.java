package dev.railroadide.railroad.utility.function;

/**
 * Represents a supplier of results that can throw checked exceptions.
 *
 * @param <T> the type of results supplied by this supplier
 */
@FunctionalInterface
public interface ThrowingSupplier<T> {
    T get() throws Exception;
}
