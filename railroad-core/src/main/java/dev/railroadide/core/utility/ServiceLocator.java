package dev.railroadide.core.utility;

import java.util.function.Function;

public final class ServiceLocator {
    private static Function<Class<?>, Object> serviceProvider;

    private ServiceLocator() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static void setServiceProvider(Function<Class<?>, Object> provider) {
        if (provider == null)
            throw new IllegalArgumentException("Service provider cannot be null");

        if (serviceProvider != null)
            throw new IllegalStateException("Service provider is already set");

        serviceProvider = provider;
    }

    public static <T> T getService(Class<T> serviceClass) {
        if (serviceProvider == null)
            throw new IllegalStateException("Service provider is not set");

        Object service = serviceProvider.apply(serviceClass);
        if (service == null)
            throw new IllegalStateException("No service found for: " + serviceClass.getName());

        return serviceClass.cast(service);
    }
}
