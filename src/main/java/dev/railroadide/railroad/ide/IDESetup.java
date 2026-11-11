package dev.railroadide.railroad.ide;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.Services;
import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.ui.RunConfigurationEditorPane;
import dev.railroadide.railroad.ide.ui.IDEPane;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.theme.ThemeManager;
import dev.railroadide.railroad.window.WindowBuilder;
import dev.railroadide.railroad.utility.javafx.JavaFXUtils;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import dev.railroadide.railroad.project.ProjectManager;
import java.util.function.Supplier;

/**
 * Creates IDE scenes and coordinates workspace and run-configuration windows.
 */
public class IDESetup {
    private static boolean isSwitchingToIDE = false;

    /**
     * Create a new IDE window for the given project.
     *
     * @param project The project to create the IDE window for
     * @return The created IDE window
     */
    public static Scene createIDEScene(Project project) {
        return constructProjectView(Railroad.PROJECT_MANAGER, project, () -> new Scene(new IDEPane(project)));
    }

    /**
     * Opens the run-configuration editor for a project.
     *
     * @param project project associated with the workspace
     * @param runConfiguration configuration to select, or null for no initial selection
     */
    public static void showEditRunConfigurationsWindow(
        @NotNull Project project,
        @Nullable RunConfiguration<?> runConfiguration
    ) {
        var editorPane = new RunConfigurationEditorPane(project);
        WindowBuilder.create()
            .owner(Railroad.WINDOW_MANAGER.getPrimaryStage())
            .title("railroad.window.ide.toolbar.edit_run_configurations", true)
            .applyPreferredSize()
            .scene(new Scene(editorPane))
            .onInit(_ -> editorPane.selectConfiguration(runConfiguration))
            .build();
    }

    /**
     * Switch to the IDE window
     * <p>
     * This method switches the window to the IDE window
     * and sets the current project to the provided project
     * and notifies the plugins of the activity
     *
     * @param project The project to switch to
     * @param stage The stage to switch to. Set to {@code null} if a new stage with a transition is required
     */
    public static void switchToIDE(Project project, @Nullable Stage stage) {
        if (isSwitchingToIDE)
            return; // Prevent multiple simultaneous IDE window creations

        isSwitchingToIDE = true;

        Runnable switchAction = () -> {
            try {
                Stage ideStage = stage == null ? Railroad.WINDOW_MANAGER.getPrimaryStage() : stage;
                Scene previousScene = ideStage.getScene();
                var idePane = constructProjectView(Railroad.PROJECT_MANAGER, project, () -> new IDEPane(project));
                if (idePane == null)
                    return;

                disposePreviousScene(previousScene);
                var ideScene = new Scene(idePane);

                ideStage.setTitle(Services.APPLICATION_INFO.getName() + " " + Services.APPLICATION_INFO.getVersion()
                    + " - " + project.getAlias());
                ideStage.setResizable(true);
                ideStage.setMaximized(true);

                if (stage == null) {
                    ThemeManager.prepareSceneTransition(previousScene, ideScene);
                    ideStage.setScene(ideScene);
                    Railroad.WINDOW_MANAGER.setPrimaryStage(ideStage);
                } else {
                    ThemeManager.prepareSceneTransition(previousScene, ideScene);
                    ideStage.setScene(ideScene);
                    Railroad.WINDOW_MANAGER.showPrimary(stage, ideScene, stage.getTitle());
                }
                ThemeManager.release(previousScene);
            } finally {
                isSwitchingToIDE = false;
            }
        };

        JavaFXUtils.runOnApplicationThread(switchAction);
    }

    /** Constructs a project view at the accepted project transition boundary. */
    public static <T> T constructProjectView(
        ProjectManager manager,
        Project project,
        Supplier<T> factory
    ) {
        Project previous = manager.getOpenProject();
        if (previous != null && previous != project) {
            previous.close();
            if (manager.getOpenProject() != null)
                return null;
        }
        boolean opening = manager.getOpenProject() != project;
        try {
            manager.setCurrentProject(project);
            return factory.get();
        } catch (RuntimeException | Error failure) {
            if (opening && manager.getOpenProject() == project) {
                try {
                    manager.setCurrentProject(null);
                } catch (RuntimeException | Error cleanup) {
                    Throwable cause = failure;
                    cause.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
    }

    private static void disposePreviousScene(@Nullable Scene previousScene) {
        if (previousScene == null)
            return;

        if (previousScene.getRoot() instanceof IDEPane idePane) {
            try {
                idePane.close();
            } catch (RuntimeException exception) {
                Railroad.LOGGER.error("Failed to dispose the previous IDE workspace cleanly", exception);
            }
        }

        Services.UI_MANAGER.releaseScene(previousScene);
    }
}
