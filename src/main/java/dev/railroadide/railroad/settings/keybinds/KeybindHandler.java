package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.registry.RegistryManager;
import dev.railroadide.core.settings.keybinds.Keybind;
import dev.railroadide.core.settings.keybinds.KeybindContexts;
import dev.railroadide.core.settings.keybinds.KeybindData;
import javafx.scene.Node;
import javafx.scene.input.KeyEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeybindHandler {
    private static final Registry<Keybind> KEYBIND_REGISTRY = RegistryManager.createRegistry("keybinds", Keybind.class);

    /**
     * Registers the provided node to capture key events.
     *
     * @param context     The context of the node.
     * @param captureNode The node that will capture key events.
     * @param <T>         The type of the node, which must extend Node.
     */
    public static <T extends Node> void registerCapture(KeybindContexts.KeybindContext context, T captureNode) {
        KEYBIND_REGISTRY.values().forEach(keybind -> {
            if (keybind.getValidContexts().contains(context) || keybind.getValidContexts().contains(KeybindContexts.ALL)) {
                captureNode.addEventHandler(KeyEvent.KEY_PRESSED, keyEvent -> {
                    if (keybind.matches(keyEvent)) {
                        keybind.getActions().forEach((keybindContext, action) -> {
                            if (keybindContext.equals(context)) {
                                action.accept(captureNode);
                            }
                        });
                    }
                });
            }
        });
    }

    /**
     * Returns a map of all keybinds with their default key combinations.
     *
     * @return A map where the key is the keybind ID and the value is a list of KeybindData representing the default key combinations.
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
