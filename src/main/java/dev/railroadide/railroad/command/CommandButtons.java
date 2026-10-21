package dev.railroadide.railroad.command;

import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.scene.control.ButtonBase;

import java.util.function.Supplier;

/**
 * Connects toolbar buttons to commands; dependencies keep enablement current.
 */
public final class CommandButtons {
    private CommandButtons() {
    }

    /**
     * Connects this control to shared command dispatch and presentation state.
     *
     * @param <T> type of the invocation argument
     * @param button toolbar button to connect
     * @param command command definition to invoke
     * @param context current project, source, and target
     * @param dependencies observables whose changes refresh button enablement
     */
    public static <T> void bind(
        ButtonBase button,
        Command<T> command,
        Supplier<CommandContext<T>> context,
        Observable... dependencies
    ) {
        button.setOnAction(_ -> CommandDispatcher.execute(command, context.get()));
        button.getProperties().put("railroad:command-id", command.id());
        if (dependencies.length > 0) {
            button.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !command.canExecute(context.get()), dependencies));
        }
    }
}
