package dev.railroadide.railroad.command;

import dev.railroadide.railroad.settings.keybinds.Keybind;
import dev.railroadide.railroad.settings.keybinds.KeybindData;
import dev.railroadide.railroad.settings.keybinds.KeybindHandler;
import dev.railroadide.railroad.ui.localized.LocalizedMenuItem;
import javafx.collections.ListChangeListener;
import javafx.collections.WeakListChangeListener;
import javafx.scene.control.MenuItem;
import javafx.scene.input.KeyCode;

import java.util.List;
import java.util.function.Supplier;

/**
 * Binds menu presentation and configurable accelerators to shared command definitions.
 */
public final class CommandMenuItems {
    private CommandMenuItems() {
    }

    /**
     * Creates a localized menu item backed by a command.
     *
     * @param <T> type of the invocation argument
     * @param command command definition to invoke
     * @param contextSupplier supplier resolving the current invocation target
     * @return configured menu item
     */
    public static <T> LocalizedMenuItem create(Command<T> command, Supplier<CommandContext<T>> contextSupplier) {
        var item = new LocalizedMenuItem(command.displayNameKey());
        bind(item, command, contextSupplier);
        return item;
    }

    /**
     * Connects this control to shared command dispatch and presentation state.
     *
     * @param <T> type of the invocation argument
     * @param item menu item to connect
     * @param command command definition to invoke
     * @param contextSupplier supplier resolving the current invocation target
     */
    public static <T> void bind(
        MenuItem item,
        Command<T> command,
        Supplier<CommandContext<T>> contextSupplier
    ) {
        item.setOnAction(_ -> CommandDispatcher.execute(command, contextSupplier.get()));
        item.getProperties().put("railroad:command-id", command.id());
        item.setOnMenuValidation(_ -> {
            if (!item.disableProperty().isBound()) {
                item.setDisable(!command.canExecute(contextSupplier.get()));
            }
        });
        bindConfiguredAccelerator(item, command);
    }

    private static void bindConfiguredAccelerator(MenuItem item, Command<?> command) {
        Keybind keybind = KeybindHandler.getKeybind(command.id());
        if (keybind == null) {
            updateAccelerator(item, command.defaultShortcuts());
            return;
        }

        ListChangeListener<KeybindData> listener = _ -> updateAccelerator(item, keybind.getKeys());
        keybind.getKeys().addListener(new WeakListChangeListener<>(listener));
        item.getProperties().put("railroad:command-keybind-listener", listener);
        updateAccelerator(item, keybind.getKeys());
    }

    private static void updateAccelerator(MenuItem item, List<KeybindData> bindings) {
        bindings.stream()
            .filter(binding -> binding.keyCode() != null && binding.keyCode() != KeyCode.UNDEFINED)
            .findFirst()
            .map(KeybindData::getKeyCodeCombination)
            .ifPresentOrElse(
                item::setAccelerator,
                () -> item.setAccelerator(null));
    }
}
