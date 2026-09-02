package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.railroad.registry.Registry;
import dev.railroadide.railroad.registry.RegistryManager;
import javafx.scene.Node;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        KEYBIND_REGISTRY.values().forEach(keybind -> {
            if (keybind.getValidContexts().contains(context)
                || keybind.getValidContexts().contains(KeybindContexts.ALL)) {
                captureNode.addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
                    keybind.findMatchingBinding(keyEvent).ifPresent(binding -> {
                        var action = keybind.getActions().get(context);
                        if (action != null) {
                            action.accept(new KeybindActionContext(
                                keybind,
                                context,
                                binding,
                                keyEvent,
                                captureNode));
                        }
                    });
                });
            }
        });
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
