package dev.railroadide.railroad.command;

import dev.railroadide.railroad.ide.WorkspaceMode;
import dev.railroadide.railroad.utility.DesktopUtils;

import java.util.List;
import java.util.function.Consumer;

/**
 * Shared application link and extension workspace actions.
 */
public final class ApplicationCommands {
    /**
     * Prevents instantiation of command definitions.
     */
    private ApplicationCommands() {
    }

    /**
     * Opens a link in the external browser using the existing URL helper.
     */
    public static final Command<String> OPEN_LINK = CommandRegistry.action(
        "railroad:open_link", "railroad.command.open_link", String.class,
        url -> !url.isBlank(), DesktopUtils::openUrl);
    /**
     * Requests a registered extension workspace mode through its existing callback.
     */
    public static final Command<ModeRequest> WORKSPACE_MODE = CommandRegistry.register(new Command<>(
        "railroad:workspace_mode", "railroad.menu.view.mode",
        c -> c.argument() != null && c.argument().mode().isAvailable(c.project()),
        c -> c.argument().requester().accept(c.argument().mode()), List.of(), ModeRequest.class));

    /**
     * Invocation target for extension-provided workspace modes.
     *
     * @param mode requested registered mode
     * @param requester existing workspace transition callback
     */
    public record ModeRequest(WorkspaceMode mode, Consumer<WorkspaceMode> requester) {
    }

    /**
     * Initializes shared application command definitions.
     */
    public static void initialize() {
    }
}
