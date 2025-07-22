package dev.railroadide.core.settings.keybinds;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.util.Pair;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class Keybind { //todo make a builder, and hold a default & current value/valuelist
    private final List<Pair<KeyCode, KeyCombination.Modifier[]>> keys;
    @Getter
    private final List<KeybindContexts.KeybindContext> validContexts;
    @Getter
    private Map<KeybindContexts.KeybindContext, Consumer<Node>> actions = new HashMap<>(); //todo possibly pass in event too

    public Keybind(List<Pair<KeyCode, KeyCombination.Modifier[]>> keys, List<KeybindContexts.KeybindContext> contexts) {
        this.keys = keys;
        this.validContexts = contexts;
    }

    public boolean matches(KeyEvent keyEvent) {
        for (Pair<KeyCode, KeyCombination.Modifier[]> key : keys) {
            KeyCode keyCode = key.getKey();
            KeyCombination.Modifier[] modifiers = key.getValue();

            if (keyCode != keyEvent.getCode()) continue;

            for (KeyCombination.Modifier modifier : modifiers) {
                if (!keyEvent.isShortcutDown() && modifier == KeyCombination.SHORTCUT_DOWN) continue;
                if (!keyEvent.isControlDown() && modifier == KeyCombination.CONTROL_DOWN) continue;
                if (!keyEvent.isShiftDown() && modifier == KeyCombination.SHIFT_DOWN) continue;
                if (!keyEvent.isAltDown() && modifier == KeyCombination.ALT_DOWN) continue;
            }

            return true;
        }

        return false;
    }

    public void addAction(KeybindContexts.KeybindContext context, Consumer<Node> action) {
        actions.put(context, action);
    }
}
