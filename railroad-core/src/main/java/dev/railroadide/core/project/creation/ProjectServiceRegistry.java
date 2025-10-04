package dev.railroadide.core.project.creation;

import java.util.HashMap;
import java.util.Map;

public class ProjectServiceRegistry {
    private final Map<Class<?>, Object> services = new HashMap<>();

    public <T> void bind(Class<T> type, T instance) {
        services.put(type, instance);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        Object service = services.get(type);
        if (service == null)
            throw new IllegalStateException("No binding for " + type);

        return (T) service;
    }
}
