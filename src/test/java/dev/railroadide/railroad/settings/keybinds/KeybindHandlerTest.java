package dev.railroadide.railroad.settings.keybinds;

import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class KeybindHandlerTest {
    @Test
    public void nestedCaptureTakesPriorityAndFallsBackToParent() {
        var parentContext = KeybindContexts.of("test:parent-" + System.nanoTime());
        var childContext = KeybindContexts.of("test:child-" + System.nanoTime());
        var received = new AtomicReference<String>();
        var parentBinding = Keybind.builder().id("test:parent-" + System.nanoTime())
            .category(new KeybindCategory("test", "test")).addDefaultKey(KeyCode.F8)
            .addAction(parentContext, _ -> received.set("parent")).build();
        var childBinding = Keybind.builder().id("test:child-" + System.nanoTime())
            .category(new KeybindCategory("test", "test")).addDefaultKey(KeyCode.F8)
            .addAction(childContext, _ -> received.set("child")).build();
        parentBinding.resetKeys();
        childBinding.resetKeys();
        KeybindHandler.registerKeybind(parentBinding);
        KeybindHandler.registerKeybind(childBinding);
        try {
            var parent = new Pane();
            var child = new Pane();
            var target = new Pane();
            parent.getChildren().add(child);
            child.getChildren().add(target);
            KeybindHandler.registerCapture(parentContext, parent);
            KeybindHandler.registerCapture(childContext, child);
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F8,
                false, false, false, false));
            assertEquals("child", received.get());
            childBinding.getKeys().clear();
            Event.fireEvent(target, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F8,
                false, false, false, false));
            assertEquals("parent", received.get());
        } finally {
            KeybindHandler.unregisterKeybind(parentBinding);
            KeybindHandler.unregisterKeybind(childBinding);
        }
    }

    @Test
    public void actionReceivesMatchedInputContext() {
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
