package dev.railroadide.railroad.utility.function;

/**
 * Represents a supplier of results that can throw checked exceptions.
 *
 * @param <T> the type of results supplied by this supplier
 */
@FunctionalInterface
public interface ThrowingSupplier<T> {
    /**
     * Gets a result.
     *
     * @return a result
     * @throws Exception if unable to produce a result
     */
    T get() throws Exception;
}
