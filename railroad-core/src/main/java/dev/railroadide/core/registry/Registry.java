package dev.railroadide.core.registry;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * A generic registry interface for managing items of type T.
 * This interface provides methods to register, retrieve, check existence, and list all items.
 *
 * @param <T> the type of items managed by the registry
 */
public interface Registry<T> {
    /**
     * Returns the unique identifier of the registry.
     *
     * @return the identifier of the registry
     */
    String getId();

    /**
     * Returns the type of items managed by the registry.
     *
     * @return the type of items in the registry
     */
    Type getType();

    /**
     * Registers an item in the registry.
     *
     * @param id   the identifier for the item
     * @param item the item to register
     * @return the registered item, which may be the same as the input item or a modified version
     */
    T register(String id, T item);

    /**
     * Unregisters an item from the registry by its identifier.
     *
     * @param id the identifier of the item to unregister
     * @return the unregistered item if it was found, or null if it was not registered
     */
    T unregister(String id);

    /**
     * Retrieves an item from the registry by its identifier.
     *
     * @param id the identifier of the item
     * @return the item if found, or null if not found
     */
    T get(String id);

    /**
     * Checks if an item is registered in the registry.
     *
     * @param id the identifier of the item
     * @return true if the item is registered, false otherwise
     */
    boolean contains(String id);

    /**
     * Retrieves all items currently registered in the registry.
     *
     * @return a list of all registered items
     */
    List<T> values();

    /**
     * Retrieves all keys (identifiers) of the items registered in the registry.
     *
     * @return a list of all keys
     */
    List<String> keys();

    /**
     * Retrieves a map of all entries in the registry, where the key is the identifier and the value is the item.
     *
     * @return a map containing all registered items with their identifiers
     */
    Map<String, T> entries();
}
