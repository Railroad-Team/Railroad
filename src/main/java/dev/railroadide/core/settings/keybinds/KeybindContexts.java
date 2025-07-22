package dev.railroadide.core.settings.keybinds;

import dev.railroadide.core.logger.LoggerServiceLocator;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

public class KeybindContexts {
    private static final Map<String, KeybindContext> contexts = new HashMap<>();

    public static KeybindContext ALL = new KeybindContext("all");

    static {
        registerContext(ALL);
    }

    public static KeybindContext registerContext(KeybindContext context) {
        if (contexts.containsKey(context.getId())) {
            LoggerServiceLocator.getInstance().getLogger().warn("Attempted to register a duplicate keybind context: {}", context);
            return getContext(context.getId());
        }
        contexts.put(context.getId(), context);

        return context;
    }

    public static KeybindContext of(String id) {
        if (getContext(id) != null) return getContext(id);

        return registerContext(new KeybindContext(id));
    }

    private static KeybindContext getContext(String id) {
        return contexts.get(id);
    }

    public static class KeybindContext {
        @Getter
        private final String id;

        public KeybindContext(String id) {
            this.id = id;
        }
    }
}