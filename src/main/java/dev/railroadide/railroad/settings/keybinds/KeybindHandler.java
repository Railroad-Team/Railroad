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
import java.util.stream.Collectors;

public class KeybindHandler {
    private static final Registry<Keybind> KEYBIND_REGISTRY = RegistryManager.createRegistry("keybinds", Keybind.class);

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

    public static Map<String, List<KeybindData>> getDefaults() {
        var map = new HashMap<String, List<KeybindData>>();

        for (Keybind keybind : KEYBIND_REGISTRY.values()) {
            map.put(keybind.getId(), keybind.getDefaultKeys().stream()
                    .map(pair -> new KeybindData(pair.keyCode(), pair.modifiers()))
                    .collect(Collectors.toList()));
        }

        return map;
    }

    public static Keybind registerKeybind(Keybind keybind) {
        KEYBIND_REGISTRY.register(keybind.getId(), keybind);
        return keybind;
    }

    public static void unregisterKeybind(Keybind keybind) {
        KEYBIND_REGISTRY.unregister(keybind.getId());
    }

    public static Keybind getKeybind(String id) {
        return KEYBIND_REGISTRY.get(id);
    }
}
