package dev.railroadide.railroad.utility;

import dev.railroadide.core.ui.RRMenuBar;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.settings.ui.SettingsPane;
import dev.yodaforce.MenuToolkit;
import dev.yodaforce.dialogs.about.AboutStageBuilder;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class MacUtils {
    private static MenuBar bar;
    public static void initialize() {
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            return;
        }

        var toolkit = MenuToolkit.toolkit();

        AboutStageBuilder builder = AboutStageBuilder.start("About Railroad")
                .withAppName("Railroad")
                .withCopyright("GNU General Public License v3.0")
                .withVersionString("1.0.0")
                .withImage(new Image(Railroad.getResourceAsStream("images/logo.png")));

        Menu appMenu = toolkit.createDefaultApplicationMenu("Railroad", builder.build(), actionEvent -> {
            Platform.runLater(() -> {
                var settingsStage = new Stage();
                settingsStage.setTitle("Settings");
                var settingsPane = new SettingsPane();
                var scene = new Scene(settingsPane, 1000, 600);
                Railroad.handleStyles(scene);
                settingsStage.setScene(scene);
                settingsStage.show();
            });
        });

        bar = new RRMenuBar(true, appMenu);
    }

    public static void show(Stage stage) {
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) return;

        MenuToolkit.toolkit().setMenuBar(stage, bar);
    }
}
