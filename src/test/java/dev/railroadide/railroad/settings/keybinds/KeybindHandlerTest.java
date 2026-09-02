package dev.railroadide.railroad.settings.keybinds;

import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class KeybindHandlerTest {
    @Test
    void actionReceivesMatchedInputContext() {
        var logicalContext = KeybindContexts.of("test:action-context-" + System.nanoTime());
        var invocation = new AtomicReference<KeybindActionContext>();
        Keybind keybind = Keybind.builder()
            .id("test:action-context-" + System.nanoTime())
            .category(new KeybindCategory("test", "test"))
            .addDefaultKey(KeyCode.DIGIT3, KeyCombination.CONTROL_DOWN)
            .addAction(logicalContext, invocation::set)
            .build();
        keybind.resetKeys();
        KeybindHandler.registerKeybind(keybind);

        try {
            var captureNode = new Pane();
            KeybindHandler.registerCapture(logicalContext, captureNode);
            Event.fireEvent(captureNode, new KeyEvent(
                KeyEvent.KEY_PRESSED,
                "3",
                "3",
                KeyCode.DIGIT3,
                false,
                true,
                false,
                false));

            KeybindActionContext actionContext = invocation.get();
            assertSame(keybind, actionContext.keybind());
            assertSame(logicalContext, actionContext.context());
            assertSame(keybind.getKeys().getFirst(), actionContext.binding());
            assertSame(captureNode, actionContext.target());
            assertEquals(KeyCode.DIGIT3, assertInstanceOf(KeyEvent.class, actionContext.event()).getCode());
        } finally {
            KeybindHandler.unregisterKeybind(keybind);
        }
    }
}
