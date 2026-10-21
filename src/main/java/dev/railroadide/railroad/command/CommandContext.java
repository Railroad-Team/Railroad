package dev.railroadide.railroad.command;

import dev.railroadide.railroad.plugin.spi.dto.Project;
import javafx.scene.Node;

/**
 * The project, source node, and explicit target for one command invocation.
 *
 * @param <T> type of the command argument
 * @param project project owning the action, or null for application actions
 * @param source originating node, or null when there is no UI source
 * @param argument explicit action target, or null for commands using the active selection
 */
public record CommandContext<T>(
    Project project,
    Node source,
    T argument
) {
    /**
     * Creates an invocation that uses the active target for a project.
     *
     * @param <T> type of the invocation argument
     * @param project owning project, or null for application actions
     * @param source originating node, or null
     * @return invocation with no explicit argument
     */
    public static <T> CommandContext<T> forProject(Project project, Node source) {
        return new CommandContext<>(project, source, null);
    }

    /**
     * Creates an invocation for an explicit action target.
     *
     * @param <T> type of the invocation argument
     * @param project owning project, or null for application actions
     * @param source originating node, or null
     * @param argument explicit action target, or null
     * @return invocation containing the supplied target
     */
    public static <T> CommandContext<T> withArgument(
        Project project,
        Node source,
        T argument
    ) {
        return new CommandContext<>(project, source, argument);
    }
}
