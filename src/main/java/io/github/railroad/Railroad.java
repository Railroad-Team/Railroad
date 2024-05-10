package io.github.railroad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.railroad.minecraft.ForgeVersion;
import io.github.railroad.minecraft.MinecraftVersion;
import io.github.railroad.project.ProjectManager;
import io.github.railroad.project.ui.project.newProject.NewProjectPane;
import io.github.railroad.project.ui.welcome.WelcomePane;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import okhttp3.OkHttpClient;

import java.io.InputStream;
import java.net.URL;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class Railroad extends Application {
    public static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static boolean DEBUG = false;
    private final AtomicReference<NewProjectPane> newProjectPane = new AtomicReference<>();

    public static final ProjectManager PROJECT_MANAGER = new ProjectManager();

    @Override
    public void start(Stage primaryStage) {
        MinecraftVersion.load();
        ForgeVersion.load();

        var welcomePane = new WelcomePane();
        var scene = new Scene(welcomePane, 1024, 768);
        handleStyles(scene);

        primaryStage.setMinWidth(scene.getWidth() + 10);
        primaryStage.setMinHeight(scene.getHeight() + 10);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Welcome to Railroad");
        primaryStage.show();

        welcomePane.getHeaderPane().getNewProjectButton().setOnAction(event -> {
            var newProjectPane = this.newProjectPane.updateAndGet(
                    pane -> Objects.requireNonNullElseGet(pane, NewProjectPane::new));

            scene.setRoot(newProjectPane);

            newProjectPane.getBackButton().setOnAction(event1 -> scene.setRoot(welcomePane));
        });
    }

    private static void handleStyles(Scene scene) {
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
