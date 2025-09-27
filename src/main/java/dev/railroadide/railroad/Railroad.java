package dev.railroadide.railroad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.railroadide.logger.Logger;
import dev.railroadide.logger.LoggerManager;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.PluginManager;
import dev.railroadide.railroad.plugin.defaults.DefaultEventBus;
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
import dev.railroadide.railroad.utility.ShutdownHooks;
import dev.railroadide.railroad.vcs.RepositoryManager;
import dev.railroadide.railroad.welcome.WelcomePane;
import dev.railroadide.railroad.window.WindowManager;
import dev.railroadide.railroadpluginapi.event.EventBus;
import dev.railroadide.railroadpluginapi.events.ApplicationStartEvent;
import dev.railroadide.railroadpluginapi.events.ApplicationStopEvent;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import okhttp3.OkHttpClient;

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
    public static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    public static final Logger LOGGER = LoggerManager.create(Railroad.class)
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
    public static final WindowManager WINDOW_MANAGER = new WindowManager();
    private static HostServices hostServices;

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
        showErrorAlert(title, header, content, buttonType -> {
        });
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
        WINDOW_MANAGER.setPrimaryStage(primaryStage);

        hostServices = getHostServices();

        try {
            LoggerManager.init();

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
            MCPVersionService.INSTANCE.forceRefresh(true);

            L18n.loadLanguage(SettingsHandler.getValue(Settings.LANGUAGE));
            WINDOW_MANAGER.showPrimary(new Scene(new WelcomePane()), Services.APPLICATION_INFO.getName() + " " + Services.APPLICATION_INFO.getVersion());

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
