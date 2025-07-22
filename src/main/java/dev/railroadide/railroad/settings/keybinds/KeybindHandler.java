package dev.railroadide.railroad.settings.keybinds;

import dev.railroadide.core.settings.keybinds.Keybind;
import dev.railroadide.core.settings.keybinds.KeybindContexts;
import dev.railroadide.railroad.Railroad;
import javafx.scene.Node;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.List;

public class KeybindHandler {
    //TODO:
    // load from file, save to file, reset to defaults

    private static final List<Keybind> keybinds = new ArrayList<>();

    public static <T extends Node> void registerCapture(KeybindContexts.KeybindContext context, T captureNode) {
        keybinds.forEach(keybind -> {
            if (keybind.getValidContexts().contains(KeybindContexts.ALL)) {
                captureNode.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
                    if (keybind.matches(event)) {
                        keybind.getActions().forEach((keybindContext, action) -> {
                            if (keybindContext.equals(KeybindContexts.ALL)) {
                                action.accept(captureNode);
                            }
                        });
                    }
                });
            }
        });

        keybinds.forEach(keybind -> {
            if (keybind.getValidContexts().contains(context)) {
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

    public static Keybind registerKeybind(Keybind keybind) {
        keybinds.add(keybind);
        return keybind;
    }
}
