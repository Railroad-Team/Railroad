package dev.railroadide.railroad.command;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Registers unique command definitions and exposes lookup by stable identifier.
 */
public final class CommandRegistry {
    private CommandRegistry() {
        throw new UnsupportedOperationException("Registry class");
    }

    private static final Map<String, Command<?>> COMMANDS = new HashMap<>();

    /**
     * Registers a command while rejecting duplicate identifiers.
     *
     * @param <T> type of the invocation argument
     * @param command command definition to invoke
     * @return the registered command
     * @throws IllegalArgumentException if the identifier is already registered
     */
    public static <T> Command<T> register(Command<T> command) {
        if (COMMANDS.putIfAbsent(command.id(), command) != null)
            throw new IllegalArgumentException("Duplicate command ID: " + command.id());

        return command;
    }

    /**
     * Registers an action on an explicit target with no default shortcut.
     *
     * @param id stable command identifier
     * @param label translation key for the action name
     * @param type runtime target type
     * @param enabled state predicate, evaluated only for nonnull targets
     * @param handler existing operation on the target
     * @param <T> target type
     * @return registered command
     */
    public static <T> Command<T> action(
        String id,
        String label,
        Class<? super T> type,
        Predicate<T> enabled,
        Consumer<T> handler
    ) {
        return register(new Command<>(id, label,
            c -> c.argument() != null && enabled.test(c.argument()),
            c -> handler.accept(c.argument()), List.of(), type));
    }

    /**
     * Looks up a command by its stable identifier.
     *
     * @param id stable command identifier
     * @return registered command
     * @throws IllegalArgumentException if no command has the given identifier
     */
    public static Command<?> get(String id) {
        Command<?> command = COMMANDS.get(id);
        if (command == null)
            throw new IllegalArgumentException("Unknown command ID: " + id);

        return command;
    }

    /**
     * Looks up a command without throwing when it is absent.
     *
     * @param id stable command identifier
     * @return matching command, or an empty optional
     */
    public static Optional<Command<?>> find(String id) {
        return Optional.ofNullable(COMMANDS.get(id));
    }

    /**
     * Returns the registered command definitions.
     *
     * @return unmodifiable view of registered commands
     */
    public static Collection<Command<?>> all() {
        return Collections.unmodifiableCollection(COMMANDS.values());
    }
}
