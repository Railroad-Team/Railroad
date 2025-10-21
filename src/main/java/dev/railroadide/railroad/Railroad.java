package dev.railroadide.railroad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.railroadide.core.utility.ServiceLocator;
import dev.railroadide.logger.Logger;
import dev.railroadide.logger.LoggerManager;
import dev.railroadide.logger.LoggerService;
import dev.railroadide.railroad.config.ConfigHandler;
import dev.railroadide.railroad.localization.L18n;
import dev.railroadide.railroad.plugin.PluginManager;
import dev.railroadide.railroad.plugin.defaults.DefaultEventBus;
import dev.railroadide.railroad.project.LicenseRegistry;
import dev.railroadide.railroad.project.MappingChannelRegistry;
import dev.railroadide.railroad.project.ProjectManager;
import dev.railroadide.railroad.project.ProjectTypeRegistry;
import dev.railroadide.railroad.project.facet.Facet;
import dev.railroadide.railroad.project.facet.FacetTypeAdapter;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import dev.railroadide.railroad.settings.keybinds.Keybinds;
import dev.railroadide.railroad.switchboard.SwitchboardRepositories;
import dev.railroadide.railroad.theme.ThemeManager;
import dev.railroadide.railroad.utility.LocalDateTimeTypeAdapter;
import dev.railroadide.railroad.utility.ShutdownHooks;
import dev.railroadide.railroad.vcs.RepositoryManager;
import dev.railroadide.railroad.welcome.WelcomePane;
import dev.railroadide.railroad.window.WindowBuilder;
import dev.railroadide.railroad.window.WindowManager;
import dev.railroadide.railroadpluginapi.event.EventBus;
import dev.railroadide.railroadpluginapi.events.ApplicationStartEvent;
import dev.railroadide.railroadpluginapi.events.ApplicationStopEvent;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import okhttp3.OkHttpClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * The main class of the application
 * <p>
 * This class is the main class of the application and is responsible for
 * starting the application and handling the main window of the application
 */
public class Railroad extends Application {
    public static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    public static final Logger LOGGER = LoggerManager.create(Railroad.class)
        .service(LoggerService.builder()
            .logDirectory(ConfigHandler.getConfigDirectory().resolve("logs"))
            .configFile(ConfigHandler.getConfigDirectory().resolve("logger_config.json"))
            .build()
        ).build();
    public static final OkHttpClient HTTP_CLIENT_NO_FOLLOW = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build();
    public static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .registerTypeAdapter(Facet.class, new FacetTypeAdapter())
        .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeTypeAdapter())
        .create();
    public static final ProjectManager PROJECT_MANAGER = new ProjectManager();
    public static final RepositoryManager REPOSITORY_MANAGER = new RepositoryManager();
    public static final EventBus EVENT_BUS = new DefaultEventBus();
    public static final WindowManager WINDOW_MANAGER = new WindowManager();
    private static HostServices hostServices;
    private volatile Throwable startupException;

    public static void main(String[] args) {
        RailroadLauncher.launchWithPreloader(args);
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
    public void init() {
        if (hostServices == null) {
            hostServices = getHostServices();
        }

        List<InitializationStep> steps = List.of(
            new InitializationStep("Initializing logger", LoggerManager::init),
            new InitializationStep("Loading configuration", ConfigHandler::initConfig),
            new InitializationStep("Scanning plugins", () -> PluginManager.loadPlugins(ConfigHandler.getConfigDirectory().resolve("plugins"))),
            new InitializationStep("Registering keybinds", Keybinds::initialize),
            new InitializationStep("Loading settings", Settings::initialize),
            new InitializationStep("Preparing settings handler", SettingsHandler::init),
            new InitializationStep("Preparing themes", ThemeManager::init),
            new InitializationStep("Binding service locator", () -> ServiceLocator.setServiceProvider(Services::getService)),
            new InitializationStep("Loading language", () -> L18n.loadLanguage(SettingsHandler.getValue(Settings.LANGUAGE))),
            new InitializationStep("Initializing repositories", SwitchboardRepositories::initialize),
            new InitializationStep("Loading mapping channels", MappingChannelRegistry::initialize),
            new InitializationStep("Loading license registry", LicenseRegistry::initialize),
            new InitializationStep("Registering project types", ProjectTypeRegistry::initialize),
            new InitializationStep("Enabling plugins", PluginManager::enableEnabledPlugins),
            new InitializationStep("Activating ready plugins", PluginManager::loadReadyPlugins),
            new InitializationStep("Restoring settings", SettingsHandler::loadSettings),
            new InitializationStep("Registering shutdown hooks", () -> ShutdownHooks.addHook(() -> {
                try (ExecutorService executorService = HTTP_CLIENT.dispatcher().executorService()) {
                    executorService.shutdown();
                }

                HTTP_CLIENT.connectionPool().evictAll();
            }))
        );

        int totalSteps = steps.size();
        for (int stepIndex = 0; stepIndex < totalSteps; stepIndex++) {
            InitializationStep step = steps.get(stepIndex);
            notifyPreloader(new RailroadPreloader.StatusNotification(step.message(), (double) stepIndex / totalSteps));
            try {
                step.action().run();
            } catch (Throwable exception) {
                startupException = exception;
                LOGGER.error("Error during Railroad initialization step: {}", step.message(), exception);
                notifyPreloader(new RailroadPreloader.ErrorNotification("Failed: " + step.message()));
                return;
            }
        }

        notifyPreloader(new RailroadPreloader.StatusNotification("Initialization complete", 1.0));
    }

    @Override
    public void start(Stage primaryStage) {
        hostServices = getHostServices();

        if (startupException != null) {
            WindowBuilder.createExceptionAlert(
                "railroad.generic.error",
                "railroad.startup.error.title",
                startupException,
                Platform::exit
            );
            return;
        }

        try {
            WINDOW_MANAGER.showPrimary(new Scene(new WelcomePane()), Services.APPLICATION_INFO.getName() + " " + Services.APPLICATION_INFO.getVersion());
            LOGGER.info("Railroad started");
            EVENT_BUS.publish(new ApplicationStartEvent());
        } catch (Throwable exception) {
            LOGGER.error("Error starting Railroad", exception);
            WindowBuilder.createExceptionAlert(
                "railroad.generic.error",
                "railroad.startup.error.title",
                exception,
                Platform::exit
            );
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

    private record InitializationStep(String message, CheckedRunnable action) {
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
