package io.github.railroad;

import atlantafx.base.theme.PrimerDark;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.railroad.PluginManager.PluginManager;
import io.github.railroad.Plugins.Discord;
import io.github.railroad.discord.activity.RailroadActivities.RailroadActivityTypes;
import io.github.railroad.minecraft.ForgeVersion;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.project.ProjectManager;
import io.github.railroad.project.ui.welcome.WelcomePane;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import okhttp3.OkHttpClient;

import java.io.InputStream;
import java.net.URL;

public class Railroad extends Application {
    public static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final ProjectManager PROJECT_MANAGER = new ProjectManager();
    public static final PluginManager PLUGIN_MANAGER = new PluginManager();

    private static boolean DEBUG = false;
    ;
    private static Scene scene;
    private static Stage window;

    public static Scene getScene() {
        return scene;
    }

    public static Stage getWindow() {
        return window;
    }

    private static void handleStyles(Scene scene) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        // setting up debug helper style
        String debugStyles = getResource("styles/debug.css").toExternalForm();
        scene.setOnKeyReleased(event -> {
            if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.D) {
                if (DEBUG) {
                    scene.getStylesheets().remove(debugStyles);
                } else {
                    scene.getStylesheets().add(debugStyles);
                }

                DEBUG = !DEBUG;
            }
        });

        String baseTheme = getResource("styles/base.css").toExternalForm();
        scene.getStylesheets().add(baseTheme);
    }

    public static URL getResource(String path) {
        return Railroad.class.getResource("/io/github/railroad/" + path);
    }

    public static InputStream getResourceAsStream(String path) {
        return Railroad.class.getResourceAsStream("/io/github/railroad/" + path);
    }

    @Override
    public void start(Stage primaryStage) {
        PLUGIN_MANAGER.AddPlugin(new Discord());
        MinecraftVersion.load();
        ForgeVersion.load();
        PLUGIN_MANAGER.LoadAllPlugins();
        window = primaryStage;

        // Calculate the primary screen size to better fit the window
        Screen screen = Screen.getPrimary();

        double screenW = screen.getBounds().getWidth();
        double screenH = screen.getBounds().getHeight();

        // TODO: Find a better way to calculate these because it makes it weird on different sized monitors
        double windowW = Math.max(500, Math.min(screenW * 0.75, 1024));
        double windowH = Math.max(500, Math.min(screenH * 0.75, 768));

        // Start the welcome screen and window
        scene = new Scene(new Pane(), windowW, windowH);

        var welcomePane = new WelcomePane();
        scene.setRoot(welcomePane);

        handleStyles(scene);

        // Open setup and show the window
        primaryStage.setMinWidth(scene.getWidth() + 10);
        primaryStage.setMinHeight(scene.getHeight() + 10);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Railroad - 1.0.0(dev)");
        primaryStage.show();
        // FIXME window is not being focused when it open

        PLUGIN_MANAGER.NotifyPluginsOfActivity(RailroadActivityTypes.RAILROAD_DEFAULT);
    }
    @Override
    public void stop() throws Exception {
        PLUGIN_MANAGER.unLoadAllPlugins();
        System.out.println("Stopping Railroad");
        System.exit(0);
    }
}
