package dev.railroadide.railroad.plugin.defaults;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.registry.RegistryManager;
import dev.railroadide.logger.Logger;
import dev.railroadide.logger.LoggerManager;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroadpluginapi.PluginContext;
import dev.railroadide.railroadpluginapi.PluginDescriptor;
import dev.railroadide.railroadpluginapi.event.EventBus;
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

        String[] mainClassParts = descriptor.getMainClass().split("\\.");
        this.logger = LoggerManager.create(mainClassParts[mainClassParts.length - 1])
                .logDirectory(ConfigHandler.getConfigDirectory().resolve("logs"))
                .configFile(ConfigHandler.getConfigDirectory().resolve("logger_config.json"))
                .build();
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
