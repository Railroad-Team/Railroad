package dev.railroadide.railroad.gradle.ui.task;

import dev.railroadide.railroad.ide.runconfig.RunConfiguration;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationManager;
import dev.railroadide.railroad.ide.runconfig.RunConfigurationTypes;
import dev.railroadide.railroad.ide.runconfig.defaults.data.GradleRunConfigurationData;
import dev.railroadide.railroad.java.JDKManager;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.localized.LocalizedMenuItem;
import dev.railroadide.railroadplugin.dto.RailroadGradleTask;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import javafx.scene.control.ContextMenu;
import org.jetbrains.annotations.NotNull;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;

public class GradleTaskContextMenu extends ContextMenu {
    public GradleTaskContextMenu(Project project, RailroadGradleTask task) {
        super();

        var runIcon = new FontIcon(FontAwesomeSolid.PLAY);
        runIcon.getStyleClass().add("run-button");

        var runItem = new LocalizedMenuItem("railroad.runconfig.run.tooltip", runIcon);
        runItem.setOnAction(event -> {
            var runConfiguration = getOrCreateRunConfig(project, task);
            runConfiguration.run(project);
        });

        var debugIcon = new FontIcon(FontAwesomeSolid.BUG);
        debugIcon.getStyleClass().add("debug-button");

        var debugItem = new LocalizedMenuItem("railroad.runconfig.debug.tooltip", debugIcon);
        debugItem.setOnAction(event -> {
            var runConfiguration = getOrCreateRunConfig(project, task);

            runConfiguration.debug(project);
        });

        getItems().addAll(runItem, debugItem);
    }

    /**
     * Get an existing run configuration for the given Gradle task, or create a new one if it doesn't exist.
     *
     * @param project the current project
     * @param task    the Gradle task
     * @return the run configuration
     */
    public static @NotNull RunConfiguration<GradleRunConfigurationData> getOrCreateRunConfig(Project project, RailroadGradleTask task) {
        RunConfigurationManager runConfigManager = project.getRunConfigManager();
        @SuppressWarnings("unchecked")
        Optional<RunConfiguration<GradleRunConfigurationData>> existingRunConfig = runConfigManager.getConfigurations().stream()
            .filter(configuration -> hasExistingRunConfig(task, configuration))
            .map(configuration -> (RunConfiguration<GradleRunConfigurationData>) configuration)
            .findFirst();

        return existingRunConfig.orElseGet(() -> createRunConfig(task, runConfigManager));
    }

    /**
     * Create a new run configuration for the given Gradle task.
     *
     * @param task             the Gradle task
     * @param runConfigManager the run configuration manager
     * @return the newly created run configuration
     */
    public static @NotNull RunConfiguration<GradleRunConfigurationData> createRunConfig(RailroadGradleTask task, RunConfigurationManager runConfigManager) {
        var configurationData = new GradleRunConfigurationData();
        RailroadModule module = task.module();
        if (module == null || module.getGradleProject() == null)
            throw new IllegalStateException("Cannot create run configuration: module or Gradle project is null");

        configurationData.setGradleProjectPath(module.getGradleProject().getProjectDirectory().toPath());
        configurationData.setTask(task.getName());
        configurationData.setJavaHome(JDKManager.getDefaultJDK());
        configurationData.setName(module.getName() + " [" + task.getName() + "]");

        var runConfiguration = new RunConfiguration<>(RunConfigurationTypes.GRADLE, configurationData);
        runConfigManager.addConfiguration(runConfiguration);
        runConfigManager.setSelectedConfiguration(runConfiguration);
        return runConfiguration;
    }

    /**
     * Check if the given run configuration corresponds to the given Gradle task.
     *
     * @param task          the Gradle task
     * @param configuration the run configuration
     * @return true if the run configuration corresponds to the Gradle task, false otherwise
     */
    public static boolean hasExistingRunConfig(RailroadGradleTask task, RunConfiguration<?> configuration) {
        if (configuration.type() != RunConfigurationTypes.GRADLE)
            return false;

        var data = (GradleRunConfigurationData) configuration.data();
        RailroadModule module = task.module();
        if (module == null || module.getGradleProject() == null)
            return false;

        return data.getGradleProjectPath().equals(module.getGradleProject().getProjectDirectory().toPath())
            && data.getTask().equals(task.getName());
    }
}
