package io.github.railroad;

import atlantafx.base.theme.PrimerDark;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
    private static boolean DEBUG = false;

    private static Scene _scene;
    private static Stage _window;

    public static Scene getScene()
    {
        return _scene;
    }
    public static Stage getWindow()
    {
        return _window;
    }

    public static final ProjectManager PROJECT_MANAGER = new ProjectManager();

    @Override
    public void start(Stage primaryStage) {
        MinecraftVersion.load();
        ForgeVersion.load();

        _window = primaryStage;

        // Calculate the primary screen size to better fit the window
        Screen screen = Screen.getPrimary();

        double screenW = screen.getBounds().getWidth();
        double screenH = screen.getBounds().getHeight();

        double windowW = Math.max(500, Math.min(screenW * 0.75, 768));
        double windowH = Math.max(500, Math.min(screenH * 0.75, 1024));

        // Start the welcome screen and window
        _scene = new Scene(new Pane(), windowW, windowH);

        var welcomePane = new WelcomePane();
        _scene.setRoot(welcomePane);

        handleStyles(_scene);

        // Open setup and show the window
        primaryStage.setMinWidth(_scene.getWidth() + 10);
        primaryStage.setMinHeight(_scene.getHeight() + 10);
        primaryStage.setScene(_scene);
        primaryStage.setTitle("Railroad - 1.0.0(dev)");
        primaryStage.show();
        // FIXME window is not being focused when it open
    }

    private static void handleStyles(Scene scene) {
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        String debugStyles = getResource("styles/debug.css").toExternalForm();
        if (DEBUG) {
            scene.getStylesheets().add(debugStyles);
        }

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
    }

    public static URL getResource(String path) {
        return Railroad.class.getResource("/io/github/railroad/" + path);
    }

    public static InputStream getResourceAsStream(String path) {
        return Railroad.class.getResourceAsStream("/io/github/railroad/" + path);
    }
}
