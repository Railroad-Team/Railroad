package dev.railroadide.railroad.command;

import dev.railroadide.railroad.ide.ui.IDEDockItem;
import dev.railroadide.railroad.settings.keybinds.*;
import javafx.event.Event;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import javafx.scene.input.KeyCodeCombination;
import static org.junit.jupiter.api.Assertions.*;

/** Regression checks for the shared command adapters and built-in metadata. */
public class CommandMigrationTest {
    @Test
    public void menuResolvesTargetAtInvocationAndRechecksEnablement() {
        var target = new AtomicInteger(1);
        var executed = new AtomicInteger();
        var command = new Command<Integer>("test:menu", "test", c -> c.argument() > 0,
            c -> executed.addAndGet(c.argument()), List.of(), Integer.class);
        var item = new MenuItem("test");
        CommandMenuItems.bind(item, command, () -> CommandContext.withArgument(null, null, target.get()));
        target.set(3);
        item.fire();
        assertEquals(3, executed.get());
        target.set(0);
        Event.fireEvent(item, new Event(MenuItem.MENU_VALIDATION_EVENT));
        assertTrue(item.isDisable());
        item.fire();
        assertEquals(3, executed.get());
    }

    @Test
    public void menuAcceleratorFollowsRebindingAndUnbinding() {
        var command = new Command<Void>("test:accelerator", "test", _ -> true, _ -> {
        },
            List.of(new KeybindData(KeyCode.F10, new KeyCombination.Modifier[0])), Void.class);
        var keybind = KeybindHandler.registerCommand(command, new KeybindCategory("test", "test"),
            KeybindContexts.of("test:accelerator"), _ -> CommandContext.forProject(null, null));
        try {
            var item = new MenuItem("test");
            CommandMenuItems.bind(item, command, () -> CommandContext.forProject(null, null));
            assertEquals(KeyCode.F10, ((KeyCodeCombination) item.getAccelerator()).getCode());
            keybind.getKeys().setAll(new KeybindData(KeyCode.F9, new KeyCombination.Modifier[0]));
            assertEquals(KeyCode.F9, ((KeyCodeCombination) item.getAccelerator()).getCode());
            keybind.getKeys().clear();
            assertNull(item.getAccelerator());
        } finally {
            KeybindHandler.unregisterKeybind(keybind);
        }
    }

    @Test
    public void untypedDispatchRejectsWrongArgumentsBeforeCallingHandler() {
        var executions = new AtomicInteger();
        var command = CommandRegistry.action("test:typed-target", "test", Integer.class,
            _ -> true, _ -> executions.incrementAndGet());
        assertThrows(IllegalArgumentException.class, () -> CommandDispatcher.execute(command.id(),
            CommandContext.withArgument(null, null, "wrong type")));
        assertEquals(0, executions.get());
    }

    @Test
    public void builtinsHaveLocalizedNamesAndNoFindReplaceImplementation() throws Exception {
        Commands.initialize();
        for (String language : List.of("en_us", "ru_ru")) {
            Set<String> keys = new HashSet<>();
            try (var stream = getClass().getResourceAsStream("/assets/railroad/lang/" + language + ".lang")) {
                assertNotNull(stream);
                try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    reader.lines().filter(line -> line.contains("="))
                        .forEach(line -> keys.add(line.substring(0, line.indexOf('='))));
                }
            }
            for (var command : CommandRegistry.all()) {
                if (command.id().startsWith("railroad:") &&
                    (language.equals("en_us") || command.displayNameKey().startsWith("railroad.command."))) {
                    assertTrue(keys.contains(command.displayNameKey()), language + ": " + command.displayNameKey());
                }
            }
        }
        assertTrue(CommandRegistry.find("railroad:edit_find").isEmpty());
        assertTrue(CommandRegistry.find("railroad:edit_replace").isEmpty());
        assertNotEquals(Commands.REOPEN_CLOSED_EDITOR_TAB.defaultShortcuts().getFirst().getKeyCodeCombination(),
            Commands.toggleDockItem(IDEDockItem.TERMINAL).defaultShortcuts().getFirst().getKeyCodeCombination());
    }
}
