package dev.railroadide.railroad.utility;

import dev.railroadide.core.ui.RRMenuBar;
import dev.railroadide.core.utility.OperatingSystem;
import dev.railroadide.nsmenufx.MenuToolkit;
import dev.railroadide.nsmenufx.dialogs.about.AboutStageBuilder;
import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.settings.ui.SettingsPane;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class MacUtils {
    private static MenuBar bar;

    /**
     * Initializes the Mac's menu bar, which provides the Application Menu and its MenuItems
     */
    public static void initialize() {
        if (OperatingSystem.CURRENT != OperatingSystem.MAC) return;

        var toolkit = MenuToolkit.toolkit();

        AboutStageBuilder builder = AboutStageBuilder.start("About Railroad")
            .withAppName("Railroad")
            .withCopyright("GNU General Public License v3.0")
            .withVersionString("1.0.0")
            .withImage(new Image(AppResources.getResourceAsStream("images/logo.png")));

        Menu appMenu = toolkit.createDefaultApplicationMenu("Railroad", builder.build(),
            actionEvent -> SettingsPane.openSettingsWindow());

        bar = new RRMenuBar(true, appMenu);
    }

    /**
     * Shows the Mac's menu bar on the given stage. Must be called after the stage is created and shown.
     *
     * @param stage The stage to which the menu bar should be added
     */
    public static void show(Stage stage) {
        if (OperatingSystem.CURRENT != OperatingSystem.MAC) return;

        MenuToolkit.toolkit().setMenuBar(stage, bar);
    }
}
