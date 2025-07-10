package io.github.railroad.plugin.defaults;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import io.github.railroad.Railroad;
import io.github.railroad.Services;
import io.github.railroad.config.ConfigHandler;
import io.github.railroad.logger.Logger;
import io.github.railroad.logger.LoggingLevel;
import io.github.railroad.railroadpluginapi.PluginContext;
import io.github.railroad.railroadpluginapi.PluginDescriptor;
import io.github.railroad.railroadpluginapi.event.EventBus;
import io.github.railroad.railroadpluginapi.registry.Registry;
import io.github.railroad.registry.RegistryManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

public class DefaultPluginContext implements PluginContext {
    private final PluginDescriptor descriptor;
    private final EventBus eventBus;
    private Logger logger;

    private final Multimap<Class<?>, Object> extensions = ArrayListMultimap.create();

    public DefaultPluginContext(@NotNull PluginDescriptor descriptor, @NotNull EventBus bus) {
        if (descriptor == null)
            throw new IllegalArgumentException("PluginDescriptor cannot be null");

        if (bus == null)
            throw new IllegalArgumentException("EventBus cannot be null");

        this.descriptor = descriptor;
        this.eventBus = bus;

        // TODO: Remove this logger when the logging module is fully integrated
        this.logger = new Logger() {
            @Override
            public void error(String message, Object... objects) {
                Railroad.LOGGER.error(message, objects);
            }

            @Override
            public void warn(String message, Object... objects) {
                Railroad.LOGGER.warn(message, objects);
            }

            @Override
            public void info(String message, Object... objects) {
                Railroad.LOGGER.info(message, objects);
            }

            @Override
            public void debug(String message, Object... objects) {
                Railroad.LOGGER.debug(message, objects);
            }

            @Override
            public void log(String message, LoggingLevel loggingLevel, Object... objects) {
                Railroad.LOGGER.log(message, io.github.railroad.logging.LoggingLevel.valueOf(loggingLevel.name()), objects);
            }
        };
    }

    @Override
    public PluginDescriptor getDescriptor() {
        return this.descriptor;
    }

    @Override
    public EventBus getEventBus() {
        return this.eventBus;
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    @Override
    public void setLogger(Logger logger) {
        if (logger == null)
            throw new IllegalArgumentException("Logger cannot be null");

        this.logger = logger;
    }

    @Override
    public <T> void registerExtension(Class<T> extensionPoint, T extension) {
        if (extensionPoint == null)
            throw new IllegalArgumentException("Extension class cannot be null");

        if (extension == null)
            throw new IllegalArgumentException("Extension instance cannot be null");

        this.extensions.put(extensionPoint, extension);
    }

    @Override
    public <T> List<T> getExtensions(Class<T> extensionPoint) {
        return this.extensions.get(extensionPoint)
                .stream()
                .filter(extensionPoint::isInstance)
                .map(extensionPoint::cast)
                .toList();
    }

    @Override
    public Path getDataDirectory() {
        return ConfigHandler.getConfigDirectory().resolve("plugins").resolve(this.descriptor.getId());
    }

    @Override
    public <T> T getService(Class<T> serviceClass) {
        if (serviceClass == null)
            throw new IllegalArgumentException("Service class cannot be null");

        T service = Services.getService(serviceClass);
        if (service == null)
            throw new IllegalStateException("No service found for class: " + serviceClass.getName());

        return service;
    }

    @Override
    public <T> Registry<T> getRegistry(Class<T> registryClass) {
        if (registryClass == null)
            throw new IllegalArgumentException("Registry class cannot be null");

        Registry<T> registry = RegistryManager.getRegistry(registryClass);
        if (registry == null)
            throw new IllegalStateException("No registry found for class: " + registryClass.getName());

        return registry;
    }

    @Override
    public Registry<?> getRegistry(String id) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("Registry ID cannot be null or empty");

        Registry<?> registry = RegistryManager.getRegistry(id);
        if (registry == null)
            throw new IllegalStateException("No registry found with ID: " + id);

        return registry;
    }
}
