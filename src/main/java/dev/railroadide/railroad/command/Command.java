package dev.railroadide.railroad.command;

import dev.railroadide.railroad.settings.keybinds.KeybindData;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A reusable action definition whose handler is guarded by its enablement predicate.
 *
 * @param <T> type of the invocation argument
 * @param id stable identifier used by dispatchers and persisted shortcuts
 * @param displayNameKey translation key for the action name
 * @param enabled predicate evaluated against the current invocation context
 * @param handler operation to run when enabled
 * @param defaultShortcuts default keyboard or mouse combinations
 * @param argumentType runtime class used to validate untyped dispatch
 */
public record Command<T>(
    String id,
    String displayNameKey,
    Predicate<CommandContext<T>> enabled,
    Consumer<CommandContext<T>> handler,
    List<KeybindData> defaultShortcuts,
    Class<? super T> argumentType
) {
    /**
     * Checks whether this invocation is currently enabled.
     *
     * @param context current project, source, and target
     * @return whether the handler may run
     */
    public boolean canExecute(CommandContext<T> context) {
        return enabled.test(context);
    }

    /**
     * Checks enablement and executes the requested command.
     *
     * @param context current project, source, and target
     * @return true when the handler ran; false when disabled
     */
    public boolean execute(CommandContext<T> context) {
        if (!canExecute(context))
            return false;

        handler.accept(context);
        return true;
    }
}
