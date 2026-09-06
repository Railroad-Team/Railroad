package dev.railroadide.railroad.command;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.IDESetup;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.ui.setup.RunControlsPane;
import dev.railroadide.railroad.settings.keybinds.KeybindData;
import dev.railroadide.railroad.ui.id.UIIds;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Run lifecycle shared by the toolbar, menus, configuration lists, and shortcuts.
 */
public final class RunCommands {
    private RunCommands() {
    }

    /**
     * Starts or restarts the explicit or selected run configuration.
     */
    public static final Command<RunConfiguration<?>> RUN = register("run", "railroad.menu.run.run",
        c -> controls(c) != null && controls(c).canExecuteConfiguration(target(c), false),
        c -> controls(c).executeConfiguration(target(c), false), KeyCode.F5);
    /**
     * Starts or restarts the target configuration under the debugger.
     */
    public static final Command<RunConfiguration<?>> DEBUG = register("debug", "railroad.menu.run.debug",
        c -> controls(c) != null && controls(c).canExecuteConfiguration(target(c), true),
        c -> controls(c).executeConfiguration(target(c), true), KeyCode.F6);
    /**
     * Stops the explicit configuration or invokes the running-configuration chooser.
     */
    public static final Command<RunConfiguration<?>> STOP = register("stop", "railroad.menu.run.stop",
        c -> controls(c) != null && (c.argument() == null
            ? controls(c).hasRunningConfigurations()
            : controls(c).canStopConfiguration(c.argument())),
        c -> {
            if (c.argument() == null) {
                controls(c).requestStop();
            } else {
                controls(c).stopConfiguration(c.argument());
            }
        }, KeyCode.F7);
    /**
     * Stops every tracked running configuration.
     */
    public static final Command<RunConfiguration<?>> STOP_ALL = register("stop_all", "railroad.ide.toolbar.stop.all",
        c -> controls(c) != null && controls(c).hasRunningConfigurations(),
        c -> controls(c).stopAllConfigurations(), null);
    /**
     * Opens the existing run-configuration editor.
     */
    public static final Command<RunConfiguration<?>> EDIT = register("edit_run_configuration",
        "railroad.run_configuration.edit",
        c -> c.project() != null,
        c -> IDESetup.showEditRunConfigurationsWindow(c.project(), target(c)), null);
    /**
     * Removes the target run configuration from the project.
     */
    public static final Command<RunConfiguration<?>> DELETE = register("delete_run_configuration",
        "railroad.run_configuration.delete",
        c -> c.project() != null && c.argument() != null,
        c -> c.project().getRunConfigManager().removeConfiguration(c.argument()), null);

    private static RunControlsPane controls(CommandContext<?> context) {
        return Services.UI_MANAGER.lookup(UIIds.IDE.RUN_CONTROLS)
            .filter(p -> p.getProject() == context.project()).orElse(null);
    }

    private static RunConfiguration<?> target(CommandContext<RunConfiguration<?>> context) {
        if (context.argument() != null)
            return context.argument();
        var controls = controls(context);
        return controls == null ? null : controls.selectedConfiguration();
    }

    private static Command<RunConfiguration<?>> register(
        String id,
        String label,
        Predicate<CommandContext<RunConfiguration<?>>> enabled,
        Consumer<CommandContext<RunConfiguration<?>>> handler,
        KeyCode shortcut
    ) {
        return CommandRegistry.register(new Command<>("railroad:" + id, label, enabled, handler,
            shortcut == null ? List.of() : List.of(new KeybindData(shortcut, new KeyCombination.Modifier[0])),
            RunConfiguration.class));
    }

    /**
     * Initializes the built-in command definitions.
     */
    public static void initialize() {
    }
}
