package dev.railroadide.railroad.utility;

import dev.railroadide.core.ui.RRMenuBar;
import dev.railroadide.core.utility.DesktopUtils;
import dev.railroadide.railroad.Railroad;
import dev.yodaforce.MenuToolkit;
import dev.yodaforce.dialogs.about.AboutStageBuilder;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class MacUtils {
    private static MenuBar bar;
    public static void initialize() {
        //TODO either put if mac here, or when called
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            return;
        }

        MenuToolkit tk = MenuToolkit.toolkit();

        AboutStageBuilder builder = AboutStageBuilder.start("About Railroad")
                .withAppName("Railroad")
                .withCopyright("GNU General Public License v3.0")
                .withVersionString("1.0.0")
                .withImage(new Image(Railroad.getResourceAsStream("images/logo.png")));

        Menu appMenu = tk.createDefaultApplicationMenu("Railroad", builder.build(), actionEvent -> {
            System.out.println(actionEvent);
        });

        bar = new RRMenuBar(true, appMenu);
    }

    public static MenuBar addDefaults(MenuBar menuBar) {
        return menuBar;
    }

    public static void show(Stage stage) {
        MenuToolkit.toolkit().setMenuBar(stage, bar);
    }
}
