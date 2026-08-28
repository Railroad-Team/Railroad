package dev.railroadide.railroad.gradle.ui;

import dev.railroadide.railroad.Services;
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

public class GradleProjectContextMenu extends ContextMenu {
    public GradleProjectContextMenu(Project project, RailroadModule module) {
        super();

        var openGradleConfig = new LocalizedMenuItem("railroad.gradle.tools.ctx_menu.open_gradle_config",
            new FontIcon(RailroadBrandsIcon.GRADLE));
        openGradleConfig.setOnAction(_ -> {
            Path buildFile = findBuildScript(module);
            if (buildFile == null)
                return;

            Services.EDITOR_TAB_MANAGER.open(buildFile);
        });

        var syncItem = new LocalizedMenuItem("railroad.gradle.tools.ctx_menu.sync",
            new FontIcon(FontAwesomeSolid.SYNC));
        syncItem.setOnAction(_ -> project.getGradleManager().getGradleModelService().refreshModel(true));

        getItems().addAll(openGradleConfig, syncItem);
    }

    private Path findBuildScript(RailroadModule module) {
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
