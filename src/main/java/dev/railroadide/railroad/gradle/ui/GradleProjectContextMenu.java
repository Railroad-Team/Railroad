package dev.railroadide.railroad.gradle.ui;

import dev.railroadide.railroad.command.CommandContext;
import dev.railroadide.railroad.command.CommandMenuItems;
import dev.railroadide.railroad.command.GradleCommands;
import dev.railroadide.railroad.plugin.spi.dto.Project;
import dev.railroadide.railroad.ui.localized.LocalizedMenuItem;
import dev.railroadide.railroad.utility.icon.RailroadBrandsIcon;
import dev.railroadide.railroadplugin.dto.RailroadModule;
import javafx.scene.control.ContextMenu;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleScript;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.nio.file.Path;

/**
 * A context menu for Gradle projects, providing options to open the Gradle configuration and synchronize the project.
 */
public class GradleProjectContextMenu extends ContextMenu {
    /**
     * Constructs a new GradleProjectContextMenu for the specified project and module.
     *
     * @param project the project associated with this context menu
     * @param module the RailroadModule associated with this context menu
     */
    public GradleProjectContextMenu(Project project, RailroadModule module) {
        super();

        var openGradleConfig = new LocalizedMenuItem("railroad.gradle.tools.ctx_menu.open_gradle_config",
            new FontIcon(RailroadBrandsIcon.GRADLE));
        CommandMenuItems.bind(openGradleConfig, GradleCommands.OPEN_BUILD_SCRIPT,
            () -> CommandContext.withArgument(project, null, module));

        var syncItem = new LocalizedMenuItem("railroad.gradle.tools.ctx_menu.sync",
            new FontIcon(FontAwesomeSolid.SYNC));
        CommandMenuItems.bind(syncItem, GradleCommands.SYNC,
            () -> CommandContext.withArgument(project, null, project.getGradleManager()));

        getItems().addAll(openGradleConfig, syncItem);
    }

    /**
     * Resolves a module build script from its Gradle model.
     *
     * @param module target module
     * @return build-script path, or null when unavailable
     */
    public static Path findBuildScript(RailroadModule module) {
        if (module == null || module.getProjectDir() == null)
            return null;

        GradleProject gradleProject = module.getGradleProject();
        if (gradleProject == null)
            return null;

        GradleScript buildScript = gradleProject.getBuildScript();
        if (buildScript == null)
            return null;

        File sourceFile = buildScript.getSourceFile();
        if (sourceFile == null)
            return null;

        return sourceFile.toPath();
    }
}
