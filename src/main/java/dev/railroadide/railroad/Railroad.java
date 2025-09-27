package dev.railroadide.railroad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.railroadide.core.gson.GsonLocator;
import dev.railroadide.core.localization.Language;
import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.localization.LocalizationServiceLocator;
import dev.railroadide.core.logger.LoggerServiceLocator;
import dev.railroadide.logger.Logger;
import dev.railroadide.logger.LoggerManager;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.ide.IDESetup;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.PluginManager;
import dev.railroadide.railroad.plugin.defaults.DefaultEventBus;
import dev.railroadide.railroad.project.Project;
import dev.railroadide.railroad.project.ProjectManager;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetTypeAdapter;
import dev.railroadide.railroad.project.minecraft.MinecraftVersion;
import dev.railroadide.railroad.project.minecraft.fabric.FabricApiVersionService;
import dev.railroadide.railroad.project.minecraft.fabric.FabricLoaderVersionService;
import dev.railroadide.railroad.project.minecraft.forge.ForgeVersionService;
import dev.railroadide.railroad.project.minecraft.forge.NeoforgeVersionService;
import dev.railroadide.railroad.project.minecraft.mappings.MCPVersionService;
import dev.railroadide.railroad.project.minecraft.mappings.MojmapVersionService;
import dev.railroadide.railroad.project.minecraft.mappings.ParchmentVersionService;
import dev.railroadide.railroad.project.minecraft.mappings.YarnVersionService;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import dev.railroadide.railroad.settings.keybinds.Keybinds;
import dev.railroadide.railroad.theme.ThemeDownloadManager;
import dev.railroadide.railroad.utility.MacUtils;
import dev.railroadide.railroad.utility.ShutdownHooks;
import dev.railroadide.railroad.vcs.RepositoryManager;
import dev.railroadide.railroad.welcome.WelcomePane;
import dev.railroadide.railroadpluginapi.event.EventBus;
import dev.railroadide.railroadpluginapi.events.ApplicationStartEvent;
import dev.railroadide.railroadpluginapi.events.ApplicationStopEvent;
import dev.railroadide.railroadpluginapi.events.ProjectEvent;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.Getter;
import okhttp3.OkHttpClient;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * The main class of the application
 * <p>
 * This class is the main class of the application and is responsible for
 * starting the application and handling the main window of the application
 */
public class Railroad extends Application {
    public static final OkHttpClient HTTP_CLIENT = new OkHttpClient();    public static final Logger LOGGER = LoggerManager.create(Railroad.class)
            .logDirectory(ConfigHandler.getConfigDirectory().resolve("logs"))
            .configFile(ConfigHandler.getConfigDirectory().resolve("logger_config.json"))
            .build();
    public static final OkHttpClient HTTP_CLIENT_NO_FOLLOW = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build();
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(Facet.class, new FacetTypeAdapter())
            .create();
    public static final ProjectManager PROJECT_MANAGER = new ProjectManager();
    public static final RepositoryManager REPOSITORY_MANAGER = new RepositoryManager();
    public static final EventBus EVENT_BUS = new DefaultEventBus();
    private static boolean DEBUG = false;
    private static Scene scene; // TODO: Reconsider whether we need this as a static field
    @Getter
    private static Stage window; // TODO: Have a proper window manager instead of a static field
    private static boolean isSwitchingToIDE = false;
    private static HostServices hostServices;

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
        if (theme == null || theme.isEmpty()) {
            LOGGER.warn("Theme is null or empty, skipping theme update.");
            return;
        }

        // TODO fix this - it needs to remove the currently applied theme (should store this somewhere)
        scene.getStylesheets().remove(theme + ".css");

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
        updateTheme(SettingsHandler.getValue(Settings.THEME));

        // setting up debug helper style
        String debugStyles = getResource("styles/debug.css").toExternalForm();
        String baseTheme = getResource("styles/base.css").toExternalForm();
        String components = getResource("styles/components.css").toExternalForm();
        scene.getStylesheets().add(baseTheme);
        scene.getStylesheets().add(components);

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
     * This method switches the window to the IDE window
     * and sets the current project to the provided project
     * and notifies the plugins of the activity
     *
     * @param project The project to switch to
     */
    public static void switchToIDE(Project project) {
        if (isSwitchingToIDE)
            return; // Prevent multiple simultaneous IDE window creations

        isSwitchingToIDE = true;

        try {
            Platform.runLater(() -> {
                Railroad.window.close();
                Railroad.window = null;

                try {
                    Railroad.window = IDESetup.createIDEWindow(project);
                    PROJECT_MANAGER.setCurrentProject(project);
                    Railroad.EVENT_BUS.publish(new ProjectEvent(project, ProjectEvent.EventType.OPENED));
                } finally {
                    isSwitchingToIDE = false;
                }
            });
        } catch (Exception exception) {
            isSwitchingToIDE = false;
            throw exception;
        }
    }

    /**
     * Get the host services of the application
     *
     * @return The host services of the application
     */
    public static HostServices getHostServicess() {
        if (hostServices == null)
            throw new IllegalStateException("Host services not initialized. Call start() first.");

        return hostServices;
    }

    @Override
    public void start(Stage primaryStage) {
        hostServices = getHostServices();

        try {
            LoggerManager.init();

            LocalizationServiceLocator.setInstance(new LocalizationService() {
                @Override
                public String get(String key, Object... objects) {
                    return L18n.localize(key, objects);
                }

                @Override
                public ObjectProperty<? extends Language> currentLanguageProperty() {
                    return L18n.currentLanguageProperty();
                }

                @Override
                public boolean isKeyValid(String key) {
                    return L18n.isKeyValid(key);
                }
            });
            LoggerServiceLocator.setInstance(() -> LOGGER);
            GsonLocator.setInstance(GSON);
            ConfigHandler.initConfig();
            PluginManager.loadPlugins(ConfigHandler.getConfigDirectory().resolve("plugins"));
            Keybinds.initialize();
            Settings.initialize();
            SettingsHandler.init();

            MinecraftVersion.requestMinecraftVersions();
            FabricApiVersionService.INSTANCE.forceRefresh(true);
            FabricLoaderVersionService.INSTANCE.forceRefresh(true);
            ForgeVersionService.INSTANCE.forceRefresh(true);
            NeoforgeVersionService.INSTANCE.forceRefresh(true);
            YarnVersionService.INSTANCE.forceRefresh(true);
            MojmapVersionService.INSTANCE.forceRefresh(true);
            ParchmentVersionService.INSTANCE.listAllVersions();
            MCPVersionService.INSTANCE.forceRefresh(true);

            L18n.loadLanguage(SettingsHandler.getValue(Settings.LANGUAGE));
            window = primaryStage;

            // Calculate the primary screen size to better fit the window
            Screen screen = Screen.getPrimary();

            double screenW = screen.getBounds().getWidth();
            double screenH = screen.getBounds().getHeight();

            double windowW = screenW * 0.75;
            double windowH = screenH * 0.75;

            // Start the welcome screen and window
            scene = new Scene(new WelcomePane(), windowW, windowH);
            handleStyles(scene);

            // Create a MacOS specific Menu Bar and Application Menu
            MacUtils.initialize();

            // Open setup and show the window
            primaryStage.setMinWidth(scene.getWidth() + 10);
            primaryStage.setMinHeight(scene.getHeight() + 10);
            primaryStage.setScene(scene);
            primaryStage.setTitle("Railroad - 1.0.0(dev)");
            primaryStage.getIcons().add(new Image(getResourceAsStream("images/logo.png")));
            primaryStage.show();

            // Show the MacOS specific menu bar
            MacUtils.show(primaryStage);

            LOGGER.info("Railroad started");
            PluginManager.enableEnabledPlugins();
            PluginManager.loadReadyPlugins();
            SettingsHandler.loadSettings();
            EVENT_BUS.publish(new ApplicationStartEvent());
            ShutdownHooks.addHook(() -> {
                try (ExecutorService executorService = HTTP_CLIENT.dispatcher().executorService()) {
                    executorService.shutdown();
                }

                HTTP_CLIENT.connectionPool().evictAll();
            });
        } catch (Throwable exception) {
            LOGGER.error("Error starting Railroad", exception);
            showErrorAlert("Error", "Error starting Railroad", "An error occurred while starting Railroad.");
        }
    }

    @Override
    public void stop() {
        LOGGER.info("Stopping Railroad");
        Railroad.EVENT_BUS.publish(new ApplicationStopEvent());
        ConfigHandler.saveConfig();
        ShutdownHooks.runHooks();
        LoggerManager.shutdown();
    }
}
