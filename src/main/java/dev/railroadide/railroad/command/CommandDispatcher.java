package dev.railroadide.railroad.command;

/**
 * Executes registered commands through their common enablement checks.
 */
public final class CommandDispatcher {
    /**
     * Executes a typed command after checking its enablement predicate.
     *
     * @param command command to execute
     * @param context current invocation target
     * @param <T> invocation argument type
     * @return whether the handler ran
     */
    public static <T> boolean execute(Command<T> command, CommandContext<T> context) {
        return command.execute(context);
    }

    /**
     * Checks enablement and executes the requested command.
     *
     * @param commandId stable identifier of the registered command
     * @param context current project, source, and target
     * @return true when the handler ran; false when disabled
     */
    public static boolean execute(String commandId, CommandContext<?> context) {
        Command<?> command = CommandRegistry.get(commandId);
        Object argument = context.argument();

        if (argument != null && !command.argumentType().isInstance(argument))
            throw new IllegalArgumentException(
                "Command " + commandId +
                    " requires " + command.argumentType().getName() +
                    ", received " + argument.getClass().getName());

        return executeUnchecked(command, context);
    }

    @SuppressWarnings("unchecked")
    private static <T> boolean executeUnchecked(Command<?> command, CommandContext<?> context) {
        return ((Command<T>) command).execute((CommandContext<T>) context);
    }
}
