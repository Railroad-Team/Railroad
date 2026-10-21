package dev.railroadide.railroad.project.creation;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores project creation services under their exact service types.
 */
public class ProjectServiceRegistry {
    private final Map<Class<?>, Object> services = new HashMap<>();

    /**
     * Registers a service, replacing any existing binding for the same type.
     *
     * @param <T> the service type
     * @param type the class used as the lookup key
     * @param instance the service instance to register
     */
    public <T> void bind(Class<T> type, T instance) {
        services.put(type, instance);
    }

    /**
     * Retrieves the service bound to the exact supplied type.
     *
     * @param <T> the service type
     * @param type the class used when registering the service
     * @return the registered service instance
     * @throws IllegalStateException if the type has no non-null binding
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        Object service = services.get(type);
        if (service == null)
            throw new IllegalStateException("No binding for " + type);

        return (T) service;
    }
}
