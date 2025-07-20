package io.github.railroad.core.registry;

import lombok.Getter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RegistryImpl<T> implements Registry<T> {
    private final Map<String, T> registry = new ConcurrentHashMap<>();

    @Getter
    private final String id;
    @Getter
    private final Type type;

    RegistryImpl(String id, Type type) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Registry ID cannot be null or empty");
        if (type == null)
            throw new IllegalArgumentException("Registry type cannot be null");

        this.id = id;
        this.type = type;
    }

    @Override
    public T register(String id, T object) {
        if (object == null)
            throw new IllegalArgumentException("Cannot register null value");

        if (registry.containsKey(id))
            throw new IllegalArgumentException("Value already registered with id: " + id);

        registry.put(id, object);
        return object;
    }

    @Override
    public T unregister(String id) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("ID cannot be null or empty");

        T removed = registry.remove(id);
        if (removed == null)
            throw new IllegalArgumentException("No value registered with id: " + id);

        return removed;
    }

    @Override
    public T get(String id) {
        return registry.get(id);
    }

    @Override
    public boolean contains(String id) {
        return registry.containsKey(id);
    }

    @Override
    public List<T> values() {
        return List.copyOf(registry.values());
    }

    @Override
    public List<String> keys() {
        return registry.keySet().stream().toList();
    }

    @Override
    public Map<String, T> entries() {
        return Map.copyOf(registry);
    }

    static Class<?> rawType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        } else if (type instanceof ParameterizedType pType) {
            return (Class<?>) pType.getRawType();
        }

        return null;
    }
}
