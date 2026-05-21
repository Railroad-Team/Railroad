package dev.railroadide.railroad.registry;

import lombok.Getter;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderedRegistry<T> implements Registry<T> {
    private final Map<String, T> registry = new LinkedHashMap<>();

    @Getter
    private final String id;
    @Getter
    private final Type type;

    OrderedRegistry(String id, Type type) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Registry ID cannot be null or empty");
        if (type == null)
            throw new IllegalArgumentException("Registry type cannot be null");

        this.id = id;
        this.type = type;
    }

    @Override
    public synchronized T register(String id, T object) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("ID cannot be null or empty");

        if (object == null)
            throw new IllegalArgumentException("Cannot register null value");

        if (registry.containsKey(id))
            throw new IllegalArgumentException("Value already registered with id: " + id);

        registry.put(id, object);
        return object;
    }

    @Override
    public synchronized T unregister(String id) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("ID cannot be null or empty");

        T removed = registry.remove(id);
        if (removed == null)
            throw new IllegalArgumentException("No value registered with id: " + id);

        return removed;
    }

    @Override
    public synchronized T get(String id) {
        return registry.get(id);
    }

    @Override
    public synchronized boolean contains(String id) {
        return registry.containsKey(id);
    }

    @Override
    public synchronized List<T> values() {
        return List.copyOf(registry.values());
    }

    @Override
    public synchronized List<String> keys() {
        return List.copyOf(registry.keySet());
    }

    @Override
    public synchronized Map<String, T> entries() {
        return Map.copyOf(registry);
    }
}
