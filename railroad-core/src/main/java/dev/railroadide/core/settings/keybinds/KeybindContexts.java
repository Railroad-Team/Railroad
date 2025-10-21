package dev.railroadide.core.settings.keybinds;

import dev.railroadide.core.logger.LoggerService;
import dev.railroadide.core.utility.ServiceLocator;

import java.util.HashMap;
import java.util.Map;

public class KeybindContexts {
    private static final Map<String, KeybindContext> contexts = new HashMap<>();

    public static final KeybindContext ALL = registerContext(new KeybindContext("all"));

    /**
     * Registers a new keybind context.
     * @param context The context to register.
     * @return The registered context, or the existing context if a duplicate was attempted to be registered.
     */
    public static KeybindContext registerContext(KeybindContext context) {
        if (contexts.containsKey(context.id())) {
            ServiceLocator.getService(LoggerService.class).getLogger().warn("Attempted to register a duplicate keybind context: {}", context);
            return getContext(context.id());
        }
        contexts.put(context.id(), context);

        return context;
    }

    /**
     * Creates a keybind context with the given id.
     * @param id The id of the context, which is used to distinguish between contexts.
     * @return The keybind context with the given id, or the existing context if a context with that id already exists.
     */
    public static KeybindContext of(String id) {
        if (getContext(id) != null) return getContext(id);

        return registerContext(new KeybindContext(id));
    }

    private static KeybindContext getContext(String id) {
        return contexts.get(id);
    }

    public record KeybindContext(String id) {}
}
