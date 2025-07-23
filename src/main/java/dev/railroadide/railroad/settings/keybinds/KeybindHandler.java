package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.registry.RegistryManager;
import dev.railroadide.core.settings.Setting;
import dev.railroadide.core.settings.SettingCategory;
import dev.railroadide.core.settings.SettingCodec;
import dev.railroadide.core.settings.keybinds.Keybind;
import dev.railroadide.core.settings.keybinds.KeybindContexts;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.util.Pair;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class KeybindHandler {
    public static final Registry<Keybind> KEYBIND_REGISTRY = RegistryManager.createRegistry("keybinds", Keybind.class);

    public static void init() {
        SettingCodec codec = SettingCodec.builder("railroad:keybinds", Map.class, KeybindsList.class)
                        .id("railroad:keybinds")
                        .createNode(KeybindsList::new)
                        .nodeToValue(KeybindsList::getKeybinds)
                        .valueToNode((map, kl) -> kl.loadKeybinds(map))
                        .jsonEncoder(KeybindsList::toJson)
                        .jsonDecoder(KeybindsList::fromJson).build();
        Setting setting = Setting.builder(Map.class)
                        .id("railroad:keybinds")
                        .title("Keybinds")
                        .description("Keybinds for various actions in Railroad IDE")
                        .category(SettingCategory.builder("railroad:keybinds").build())
                        .defaultValue(getDefaults())
                        .codec(codec).addListener(KeybindHandler::update)
                        .build();
        SettingsHandler.registerSetting(setting);
    }

    private static void update(Object oldMap, Object newMap) {
        Railroad.LOGGER.info("Updated keybinds from {} to {}", oldMap, newMap);
    }

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

    public static Map<String, List<Pair<KeyCode, KeyCombination.Modifier[]>>> getDefaults() {
        var map = new HashMap<String, List<Pair<KeyCode, KeyCombination.Modifier[]>>>();

        for (Keybind keybind : KEYBIND_REGISTRY.values()) {
            map.put(keybind.getId(), keybind.getDefaultKeys().stream()
                    .map(pair -> new Pair<>(pair.getKey(), pair.getValue()))
                    .collect(Collectors.toList()));
        }

        return map;
    }

    public static Keybind registerKeybind(Keybind keybind) {
        KEYBIND_REGISTRY.register(keybind.getId(), keybind);
        return keybind;
    }
}
