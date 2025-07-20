package io.github.railroad.core.registry;

import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RegistryManager is responsible for creating, retrieving, and managing registries.
 * It allows for the creation of registries with a unique ID and type, and provides methods
 * to access and manipulate these registries.
 */
public class RegistryManager {
    private static final Map<String, Registry<?>> REGISTRIES = new ConcurrentHashMap<>();

    /**
     * Private constructor to prevent instantiation.
     */
    private RegistryManager() {
        // NO-OP
    }

    /**
     * Creates a new registry with the specified ID and type.
     *
     * @param id   the unique identifier for the registry
     * @param type the type of elements in the registry
     * @param <T>  the type of elements in the registry
     * @return a new Registry instance
     */
    public static <T> Registry<T> createRegistry(String id, Type type) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Registry ID cannot be null or empty");

        if (type == null)
            throw new IllegalArgumentException("Type cannot be null");

        Registry<T> registry = new RegistryImpl<>(id, type);
        REGISTRIES.put(id, registry);
        return registry;
    }

    /**
     * Creates a new registry with the specified ID and TypeToken.
     *
     * @param id   the unique identifier for the registry
     * @param type the TypeToken representing the type of elements in the registry
     * @param <T>  the type of elements in the registry
     * @return a new Registry instance
     */
    public static <T> Registry<T> createRegistry(String id, TypeToken<T> type) {
        if (type == null)
            throw new IllegalArgumentException("TypeToken cannot be null");

        return createRegistry(id, type.getType());
    }

    /**
     * Retrieves a registry by its ID.
     *
     * @param id the unique identifier for the registry
     * @param <T> the type of elements in the registry
     * @return the Registry instance associated with the given ID
     * @throws IllegalArgumentException if no registry is found with the specified ID
     */
    public static <T> Registry<T> getRegistry(String id) {
        @SuppressWarnings("unchecked")
        Registry<T> registry = (Registry<T>) REGISTRIES.get(id);
        if (registry == null)
            throw new IllegalArgumentException("No registry found with id: " + id);

        return registry;
    }

    /**
     * Checks if a registry exists with the specified ID.
     *
     * @param id the unique identifier for the registry
     * @return true if a registry with the given ID exists, false otherwise
     */
    public static boolean registryExists(String id) {
        return REGISTRIES.containsKey(id);
    }

    /**
     * Retrieves all registered registries.
     *
     * @return an unmodifiable map of all registries, where the key is the registry ID and the value is the Registry instance
     */
    public static Map<String, Registry<?>> getAllRegistries() {
        return Map.copyOf(REGISTRIES);
    }

    /**
     * Unregisters a registry by its ID.
     *
     * @param id the unique identifier for the registry to be unregistered
     * @throws IllegalArgumentException if no registry is found with the specified ID
     */
    public static void unregisterRegistry(String id) {
        if (!REGISTRIES.containsKey(id))
            throw new IllegalArgumentException("No registry found with id: " + id);

        REGISTRIES.remove(id);
    }

    /**
     * Retrieves a registry by its type.
     *
     * @param registryClass the class representing the type of elements in the registry
     * @param <T>           the type of elements in the registry
     * @return the Registry instance associated with the given type
     * @throws IllegalArgumentException if no registry is found for the specified type
     */
    public static <T> Registry<T> getRegistry(Type registryClass) {
        if (registryClass == null)
            throw new IllegalArgumentException("Registry class cannot be null");

        for (Map.Entry<String, Registry<?>> entry : REGISTRIES.entrySet()) {
            Registry<?> registry = entry.getValue();
            if (typesEqual(registry.getType(), registryClass)) {
                @SuppressWarnings("unchecked")
                Registry<T> typedRegistry = (Registry<T>) registry;
                return typedRegistry;
            }
        }

        throw new IllegalArgumentException("No registry found for type: " + registryClass.getTypeName());
    }

    private static boolean typesEqual(Type a, Type b) {
        return Objects.equals(a, b);
    }
}
