package dev.railroadide.railroad.command;

import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.gradle.project.GradleManager;
import dev.railroadide.railroad.gradle.ui.GradleProjectContextMenu;
import dev.railroadide.railroad.gradle.ui.GradleToolsPane;
import dev.railroadide.railroad.gradle.ui.task.GradleTaskContextMenu;
import dev.railroadide.railroadplugin.dto.RailroadGradleTask;
import dev.railroadide.railroadplugin.dto.RailroadModule;

import java.util.List;

/**
 * Command definitions for existing Gradle tool and task actions.
 */
public final class GradleCommands {
    /**
     * Prevents instantiation of command definitions.
     */
    private GradleCommands() {
    }

    /**
     * Requests a forced Gradle model refresh.
     */
    public static final Command<GradleManager> SYNC = CommandRegistry.action(
        "railroad:gradle_sync", "railroad.gradle.tools.button.sync.tooltip", GradleManager.class,
        _ -> true, m -> m.getGradleModelService().refreshModel(true));
    /**
     * Downloads source archives using the existing progress handling.
     */
    public static final Command<GradleToolsPane> DOWNLOAD_SOURCES = CommandRegistry.action(
        "railroad:gradle_download_sources", "railroad.gradle.tools.button.downloadsources.tooltip",
        GradleToolsPane.class,
        GradleToolsPane::canDownloadSources, GradleToolsPane::downloadSources);
    /**
     * Toggles and persists the Gradle offline setting.
     */
    public static final Command<GradleToolsPane> TOGGLE_OFFLINE = CommandRegistry.action(
        "railroad:gradle_toggle_offline", "railroad.gradle.tools.button.toggleoffline.tooltip", GradleToolsPane.class,
        _ -> true, GradleToolsPane::toggleOffline);
    /**
     * Opens the selected module's existing Gradle build script.
     */
    public static final Command<RailroadModule> OPEN_BUILD_SCRIPT = CommandRegistry.action(
        "railroad:gradle_open_build_script", "railroad.gradle.tools.ctx_menu.open_gradle_config", RailroadModule.class,
        m -> GradleProjectContextMenu.findBuildScript(m) != null,
        m -> Services.EDITOR_TAB_MANAGER.open(GradleProjectContextMenu.findBuildScript(m)));
    /**
     * Runs the selected task through the shared run lifecycle.
     */
    public static final Command<RailroadGradleTask> RUN_TASK = task(false);
    /**
     * Debugs the selected task through the shared run lifecycle.
     */
    public static final Command<RailroadGradleTask> DEBUG_TASK = task(true);

    /**
     * Registers a task-execution command without creating configurations during validation.
     *
     * @param debug whether to request debugging
     * @return registered task command
     */
    private static Command<RailroadGradleTask> task(boolean debug) {
        return CommandRegistry.register(new Command<>("railroad:gradle_" + (debug ? "debug" : "run") + "_task",
            "railroad.runconfig." + (debug ? "debug" : "run") + ".tooltip",
            c -> c.project() != null && c.argument() != null && c.argument().module() != null,
            c -> CommandDispatcher.execute(debug ? RunCommands.DEBUG : RunCommands.RUN,
                CommandContext.withArgument(c.project(), c.source(),
                    GradleTaskContextMenu.getOrCreateRunConfig(c.project(), c.argument()))),
            List.of(), RailroadGradleTask.class));
    }

    /**
     * Initializes the Gradle command definitions.
     */
    public static void initialize() {
    }
}
