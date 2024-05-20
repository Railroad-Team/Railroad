package io.github.railroad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.railroad.minecraft.FabricAPIVersion;
import io.github.railroad.plugin.PluginManager;
import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.minecraft.ForgeVersion;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.project.ProjectManager;
import io.github.railroad.project.ui.welcome.WelcomePane;
import io.github.railroad.utility.ConfigHandler;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import java.util.function.Consumer;

public class Railroad extends Application {
    public static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final ProjectManager PROJECT_MANAGER = new ProjectManager();
    public static final PluginManager PLUGIN_MANAGER = new PluginManager();

    private static final Logger LOGGER = LoggerFactory.getLogger(Railroad.class);
    private static boolean DEBUG = false;
    private static Scene scene;
    private static Stage window;

    public static Scene getScene() {
        return scene;
    }

    public static Stage getWindow() {
        return window;
    }

    private static void handleStyles(Scene scene) {
        Application.setUserAgentStylesheet(getResource("styles/themes/nord-dark.css").toExternalForm());

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
        ConfigHandler.updateConfig(ConfigHandler.getConfigJson());
        PLUGIN_MANAGER.start();
        PLUGIN_MANAGER.addCustomEventListener(event -> {
            Platform.runLater(() -> {
                Railroad.showErrorAlert("Plugin", event.getPlugin().getClass().getName(), event.getPhaseResult().getErrors().toString());
            });
        });

        MinecraftVersion.load();
        ForgeVersion.load();
        FabricAPIVersion.load();
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
        // FIXME window is not being focused when it opens

        LOGGER.info("Railroad started");
        PLUGIN_MANAGER.notifyPluginsOfActivity(RailroadActivities.RailroadActivityTypes.RAILROAD_DEFAULT);
    }

    @Override
    public void stop() {
        PLUGIN_MANAGER.unLoadAllPlugins();
        LOGGER.info("Stopping Railroad");
        System.exit(0);
    }

    public static void showErrorAlert(String title, String header, String content, Consumer<ButtonType> action) {
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            action.accept(result.get());
        }
    }

    public static void showErrorAlert(String title, String header, String content) {
        showErrorAlert(title, header, content, buttonType -> {});
    }
}
