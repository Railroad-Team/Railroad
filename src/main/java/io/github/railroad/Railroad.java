package io.github.railroad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.railroad.discord.DiscordCore;
import io.github.railroad.discord.activity.RailroadActivities;
import io.github.railroad.discord.activity.RailroadActivities.RailroadActivityTypes;
import io.github.railroad.minecraft.ForgeVersion;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.project.ProjectManager;
import io.github.railroad.project.ui.welcome.WelcomePane;
import io.github.railroad.utility.ConfigHandler;
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
    // App versioning
    private static final int major_ver = 1;
    private static final int minor_ver = 0;
    private static final int patch_ver = 0;
    private static final String version_tag = "dev";

    public static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final ProjectManager PROJECT_MANAGER = new ProjectManager();

    private static boolean DEBUG = false;
    private static DiscordCore DISCORD = null;
    private static Scene scene;
    private static Stage window;

    public static String getVersion() {
        if (version_tag.trim() == "")
            return String.format("%d.%d.%d", major_ver, minor_ver, patch_ver);
        else
            return String.format("%d.%d.%d-%s", major_ver, minor_ver, patch_ver, version_tag);
    }

    public static Scene getScene() {
        return scene;
    }
    public static Stage getWindow() {
        return window;
    }

    public static DiscordCore getDiscord() {
        return DISCORD;
    }
    public static boolean discordRPC() {
        return DISCORD != null;
    }

    private static DiscordCore setupDiscord() {
        //verify if it's enabled on config
        var RCPEnabled = ConfigHandler.getConfigJson().get("discordRPC").getAsBoolean();
        if (!RCPEnabled)
            return null;

        var discord = new DiscordCore("853387211897700394");

        Runtime.getRuntime().addShutdownHook(new Thread(discord::close));

        return discord;
    }

    public static URL getResource(String path) {
        return Railroad.class.getResource("/io/github/railroad/" + path);
    }

    public static InputStream getResourceAsStream(String path) {
        return Railroad.class.getResourceAsStream("/io/github/railroad/" + path);
    }

    @Override
    public void start(Stage primaryStage) {
        MinecraftVersion.load();
        ForgeVersion.load();

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
        primaryStage.setTitle("Railroad - " + getVersion());
        primaryStage.show();
        // FIXME window is not being focused when it open

        DISCORD = setupDiscord();

        //Setup main menu RP
        if (discordRPC())
            RailroadActivities.setActivity(RailroadActivityTypes.RAILROAD_DEFAULT);
    }

    private static void handleStyles(Scene scene) {
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

        // setting up base theme
        String baseTheme = getResource("styles/base.css").toExternalForm();
        scene.getStylesheets().add(baseTheme);

        // setting up user theme
        String userTheme = getResource("themes/atlantafx-themes/primer-dark.css").toExternalForm();
        scene.getStylesheets().add(userTheme);
    }

    @Override
    public void stop() throws Exception {
        //throw new Exception("Stopping Railroad!");
        System.out.println("Stopping Railroad!");
        System.exit(0);
    }
}
