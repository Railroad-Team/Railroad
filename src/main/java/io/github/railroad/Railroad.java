package io.github.railroad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.railroad.config.ConfigHandler;
import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.localization.L18n;
import io.github.railroad.plugin.PluginManager;
import io.github.railroad.project.Project;
import io.github.railroad.project.ProjectManager;
import io.github.railroad.project.minecraft.FabricAPIVersion;
import io.github.railroad.project.minecraft.ForgeVersion;
import io.github.railroad.project.minecraft.MinecraftVersion;
import io.github.railroad.project.minecraft.NeoForgeVersion;
import io.github.railroad.settings.handler.SettingsHandler;
import io.github.railroad.settings.ui.themes.ThemeDownloadManager;
import io.github.railroad.utility.ShutdownHooks;
import io.github.railroad.vcs.RepositoryManager;
import io.github.railroad.welcome.WelcomePane;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.Getter;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The main class of the application
 * <p>
 * This class is the main class of the application and is responsible for
 * starting the application and handling the main window of the application
 */
public class Railroad extends Application {
    public static final Logger LOGGER = LoggerFactory.getLogger(Railroad.class);
    public static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    public static final OkHttpClient HTTP_CLIENT_NO_FOLLOW = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final SettingsHandler SETTINGS_HANDLER = new SettingsHandler();
    public static final ProjectManager PROJECT_MANAGER = new ProjectManager();
    public static final PluginManager PLUGIN_MANAGER = new PluginManager();
    public static final RepositoryManager REPOSITORY_MANAGER = new RepositoryManager();
    public static final String URL_REGEX = "(?:http(s)?:\\/\\/)?[\\w.-]+(?:\\.[\\w\\.-]+)+[\\w\\-\\._~:/?#\\[\\]@!\\$&'\\(\\)\\*\\+,;=.]+$";

    private static boolean DEBUG = false;
    @Getter
    private static Scene scene; // TODO: Reconsider whether we need this
    @Getter
    private static Stage window; // TODO: Reconsider whether we need this

    /**
     * Update the theme of the application
     * <p>
     * This method updates the theme of the application by removing the old theme
     * and adding the new theme to the scene
     * <p>
     * This method also updates the theme in the config file
     *
     * @param theme The new theme to apply
     */
    public static void updateTheme(String theme) {
        //TODO fix this - it needs to remove the currently applied theme
        // probably not working *properly* because of new settings system
        getScene().getStylesheets().remove(SETTINGS_HANDLER.getStringSetting("railroad:theme") + ".css");

        if (theme.startsWith("default")) {
            Application.setUserAgentStylesheet(getResource("styles/" + theme + ".css").toExternalForm());
        } else {
            Application.setUserAgentStylesheet(new File(ThemeDownloadManager.getThemesDirectory() + "/" + theme + ".css").toURI().toString());
        }
    }

    /**
     * Handles the styles of the application
     * <p>
     * This method adds the base theme and debug helper styles to the scene
     * and allows the user to toggle the debug helper styles by pressing
     * Ctrl + Shift + D
     *
     * @param scene The scene to apply the styles to
     */
    public static void handleStyles(Scene scene) {
        updateTheme(SETTINGS_HANDLER.getStringSetting("railroad:theme"));

        // setting up debug helper style
        String debugStyles = getResource("styles/debug.css").toExternalForm();
        String baseTheme = getResource("styles/base.css").toExternalForm();
        scene.getStylesheets().add(baseTheme);

        scene.setOnKeyReleased(event -> {
            if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.D) {
                if (DEBUG) {
                    scene.getStylesheets().remove(debugStyles);
                } else {
                    scene.getStylesheets().add(debugStyles);
                }

                DEBUG = !DEBUG;
            } else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.R) {
                for (String style : scene.getStylesheets()) {
                    scene.getStylesheets().remove(style);
                    scene.getStylesheets().add(style);
                    break;
                }
            }
        });

        scene.getStylesheets().add(Railroad.getResource("styles/code-area.css").toExternalForm());
    }

    /**
     * Get a resource from the assets folder
     *
     * @param path The path to the resource
     * @return The URL of the resource
     */
    public static URL getResource(String path) {
        return Railroad.class.getClassLoader().getResource("assets/railroad/" + path);
    }

    /**
     * Get a resource from the assets folder as an InputStream
     *
     * @param path The path to the resource
     * @return The InputStream of the resource
     */
    public static InputStream getResourceAsStream(String path) {
        return Railroad.class.getClassLoader().getResourceAsStream("assets/railroad/" + path);
    }

    /**
     * Show an error alert
     *
     * @param title   The title of the alert
     * @param header  The header of the alert
     * @param content The content of the alert
     * @param action  The action to perform when the alert is closed
     */
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

    /**
     * Show an error alert
     *
     * @param title   The title of the alert
     * @param header  The header of the alert
     * @param content The content of the alert
     */
    public static void showErrorAlert(String title, String header, String content) {
        showErrorAlert(title, header, content, buttonType -> {});
    }

    /**
     * Switch to the IDE window
     * <p>
     *     This method switches the window to the IDE window
     *     and sets the current project to the provided project
     *     and notifies the plugins of the activity
     *
     * @param project The project to switch to
     */
    public static void switchToIDE(Project project) {
        Stage window = IDESetup.createIDEWindow(project);
        Railroad.window.close();
        Railroad.window = window;
        PROJECT_MANAGER.setCurrentProject(project);
        PLUGIN_MANAGER.notifyPluginsOfActivity(RailroadActivities.RailroadActivityTypes.RAILROAD_PROJECT_OPEN, project);
    }

    /**
     * Open a URL in the default browser
     *
     * @param url The URL to open
     */
    public static void openUrl(String url) {
        if (!url.matches(URL_REGEX)) {
            showErrorAlert("Invalid URL", "Invalid URL", "The URL provided is invalid.");
            return;
        }

        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (IOException | URISyntaxException exception) {
            showErrorAlert("Error", "Error opening URL", "An error occurred while trying to open the URL.");
        }
    }

    @Override
    public void start(Stage primaryStage) {
        try {
            ConfigHandler.initConfig();
            SETTINGS_HANDLER.initSettingsFile();
            PLUGIN_MANAGER.start();
            PLUGIN_MANAGER.addCustomEventListener(event -> {
                Platform.runLater(() -> {
                    Railroad.showErrorAlert("Plugin", event.getPlugin().getClass().getName(), event.getPhaseResult().getErrors().toString());
                });
            });
            REPOSITORY_MANAGER.start();
            MinecraftVersion.load();
            ForgeVersion.load();
            FabricAPIVersion.load();
            NeoForgeVersion.load();
            L18n.loadLanguage();
            window = primaryStage;

            // Calculate the primary screen size to better fit the window
            Screen screen = Screen.getPrimary();

            double screenW = screen.getBounds().getWidth();
            double screenH = screen.getBounds().getHeight();

            double windowW = Math.max(500, Math.min(screenW * 0.75, 1024));
            double windowH = Math.max(500, Math.min(screenH * 0.75, 768));

            // Start the welcome screen and window
            scene = new Scene(new WelcomePane(), windowW, windowH);
            handleStyles(scene);

            // Open setup and show the window
            primaryStage.setMinWidth(scene.getWidth() + 10);
            primaryStage.setMinHeight(scene.getHeight() + 10);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Railroad - 1.0.0(dev)");
            primaryStage.getIcons().add(new Image(getResourceAsStream("images/logo.png")));
            primaryStage.show();

            LOGGER.info("Railroad started");
            PLUGIN_MANAGER.notifyPluginsOfActivity(RailroadActivities.RailroadActivityTypes.RAILROAD_DEFAULT);
            ShutdownHooks.addHook(() -> {
                HTTP_CLIENT.dispatcher().executorService().shutdown();
                HTTP_CLIENT.connectionPool().evictAll();
            });
        } catch (Exception e) {
            LOGGER.error("Error starting Railroad", e);
            showErrorAlert("Error", "Error starting Railroad", "An error occurred while starting Railroad.");
        }
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping Railroad");
        PLUGIN_MANAGER.unloadPlugins();
        ConfigHandler.saveConfig();
        ShutdownHooks.runHooks();
    }
}
