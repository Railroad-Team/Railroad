package io.github.railroad.registry;

import com.google.common.reflect.TypeToken;
import io.github.railroad.railroadpluginapi.registry.Registry;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RegistryManager {
    private static final Map<String, Registry<?>> REGISTRIES = new ConcurrentHashMap<>();

    public static <T> Registry<T> createRegistry(String id, Type type) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Registry ID cannot be null or empty");

        if (type == null)
            throw new IllegalArgumentException("Type cannot be null");

        Registry<T> registry = new RegistryImpl<>(id, type);
        REGISTRIES.put(id, registry);
        return registry;
    }

    public static <T> Registry<T> createRegistry(String id, TypeToken<T> type) {
        if (type == null)
            throw new IllegalArgumentException("TypeToken cannot be null");

        return createRegistry(id, type.getType());
    }

    public static <T> Registry<T> getRegistry(String id) {
        @SuppressWarnings("unchecked")
        Registry<T> registry = (Registry<T>) REGISTRIES.get(id);
        if (registry == null)
            throw new IllegalArgumentException("No registry found with id: " + id);

        return registry;
    }

    public static boolean registryExists(String id) {
        return REGISTRIES.containsKey(id);
    }

    public static Map<String, Registry<?>> getAllRegistries() {
        return Map.copyOf(REGISTRIES);
    }

    public static void unregisterRegistry(String id) {
        if (!REGISTRIES.containsKey(id))
            throw new IllegalArgumentException("No registry found with id: " + id);

        REGISTRIES.remove(id);
    }

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
