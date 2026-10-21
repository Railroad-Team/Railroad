package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.railroad.command.Command;
import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.CommandDispatcher;
import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;
import javafx.scene.Node;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Central registry and dispatcher for application keybinds.
 */
public class KeybindHandler {
    private static final Registry<Keybind> KEYBIND_REGISTRY = RegistryManager.createOrderedRegistry("keybinds",
        Keybind.class);

    /**
     * Registers the provided node to capture key events.
     *
     * @param context The context of the node.
     * @param captureNode The node that will capture key events.
     * @param <T> The type of the node, which must extend Node.
     */
    public static <T extends Node> void registerCapture(KeybindContexts.KeybindContext context, T captureNode) {
        captureNode.getProperties().put("railroad:keybind-context", context);
        captureNode.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            Node target = event.getTarget() instanceof Node node ? node : captureNode;
            // The deepest registered capture owns dispatch and walks outward for fallback.
            for (Node node = target; node != captureNode && node != null; node = node.getParent()) {
                if (node.getProperties().containsKey("railroad:keybind-context"))
                    return;
            }
            for (Node node = captureNode; node != null; node = node.getParent()) {
                Object registered = node.getProperties().get("railroad:keybind-context");
                if (registered instanceof KeybindContexts.KeybindContext active &&
                    dispatchKeyEvent(active, event, target)) {
                    event.consume();
                    return;
                }
            }
        });
    }

    private static boolean dispatchKeyEvent(KeybindContexts.KeybindContext context, KeyEvent event, Node target) {
        for (Keybind keybind : KEYBIND_REGISTRY.values()) {
            if (!keybind.getValidContexts().contains(context) &&
                !keybind.getValidContexts().contains(KeybindContexts.ALL))
                continue;

            KeybindData binding = keybind.findMatchingBinding(event).orElse(null);
            if (binding == null)
                continue;

            Consumer<KeybindActionContext> action = keybind.getActions().get(context);
            if (action == null)
                continue;

            action.accept(new KeybindActionContext(
                keybind,
                context,
                binding,
                event,
                target));
            return true;
        }

        return false;
    }

    /**
     * Dispatches a mouse event to the first matching keybind in the supplied context.
     *
     * @param context The active keybind context.
     * @param event The mouse event to match.
     * @param target The node the contextual action should operate on.
     * @return {@code true} when a matching action was invoked.
     */
    public static boolean dispatchMouseEvent(KeybindContexts.KeybindContext context, MouseEvent event, Node target) {
        for (Keybind keybind : KEYBIND_REGISTRY.values()) {
            if (!keybind.getValidContexts().contains(context)
                && !keybind.getValidContexts().contains(KeybindContexts.ALL))
                continue;
            KeybindData binding = keybind.findMatchingBinding(event).orElse(null);
            if (binding == null)
                continue;

            var action = keybind.getActions().get(context);
            if (action != null) {
                action.accept(new KeybindActionContext(keybind, context, binding, event, target));
                return true;
            }
        }
        return false;
    }

    /**
     * Returns a map of all keybinds with their default key combinations.
     *
     * @return A map where the key is the keybind ID and the value is a list of KeybindData representing the default key
     *         combinations.
     */
    public static Map<String, List<KeybindData>> getDefaults() {
        var map = new HashMap<String, List<KeybindData>>();

        for (Keybind keybind : KEYBIND_REGISTRY.values()) {
            map.put(keybind.getId(), keybind.getDefaultKeys());
        }

        return map;
    }

    /**
     * Registers a keybind in the keybind registry.
     *
     * @param keybind The keybind to register.
     * @return The registered keybind.
     */
    public static Keybind registerKeybind(Keybind keybind) {
        KEYBIND_REGISTRY.register(keybind.getId(), keybind);
        return keybind;
    }

    /**
     * Registers a command's default shortcuts in one input scope.
     *
     * @param command command whose handler and defaults are shared
     * @param category shortcut settings category
     * @param keybindContext input scope
     * @param contextFactory resolves the target for each input event
     * @param <T> command argument type
     * @return registered keybind
     */
    public static <T> Keybind registerCommand(
        Command<T> command,
        KeybindCategory category,
        KeybindContexts.KeybindContext keybindContext,
        Function<KeybindActionContext, CommandContext<T>> contextFactory
    ) {
        return registerCommand(command, category, Map.of(keybindContext, contextFactory));
    }

    /**
     * Registers shortcuts that resolve their target and dispatch the same command handler.
     *
     * @param <T> type of the invocation argument
     * @param command command definition to invoke
     * @param category settings category for the shortcut
     * @param contextFactories context factories indexed by logical shortcut scope
     * @return registered configurable keybind
     */
    public static <T> Keybind registerCommand(
        Command<T> command,
        KeybindCategory category,
        Map<KeybindContexts.KeybindContext, Function<KeybindActionContext, CommandContext<T>>> contextFactories
    ) {
        Keybind.Builder builder = Keybind.builder()
            .id(command.id())
            .category(category)
            .ignoreAllContext();

        contextFactories.forEach((keybindContext, contextFactory) -> builder
            .addValidContext(keybindContext)
            .addAction(keybindContext, action -> CommandDispatcher.execute(
                command,
                contextFactory.apply(action))));

        command.defaultShortcuts().forEach(builder::addDefaultBinding);

        Keybind keybind = registerKeybind(builder.build());
        keybind.resetKeys();
        return keybind;
    }

    /**
     * Unregisters a keybind from the keybind registry.
     *
     * @param keybind The keybind to unregister.
     */
    public static void unregisterKeybind(Keybind keybind) {
        KEYBIND_REGISTRY.unregister(keybind.getId());
    }

    /**
     * Retrieves a keybind by its ID.
     *
     * @param id The ID of the keybind to retrieve.
     * @return The keybind associated with the given ID, or null if no such keybind exists.
     */
    public static Keybind getKeybind(String id) {
        return KEYBIND_REGISTRY.get(id);
    }
}
