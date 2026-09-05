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

public class IDESetup {
    private static boolean isSwitchingToIDE = false;

    /**
     * Create a new IDE window for the given project.
     *
     * @param project The project to create the IDE window for
     * @return The created IDE window
     */
    public static Scene createIDEScene(Project project) {
        return new Scene(new IDEPane(project));
    }

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
                var idePane = new IDEPane(project);

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

                try {
                    Railroad.PROJECT_MANAGER.setCurrentProject(project);
                } finally {
                    isSwitchingToIDE = false;
                }
            } catch (Exception exception) {
                isSwitchingToIDE = false;
                throw exception;
            }
        };

        JavaFXUtils.runOnApplicationThread(switchAction);
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
