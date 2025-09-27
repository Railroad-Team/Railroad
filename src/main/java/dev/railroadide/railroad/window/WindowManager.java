package dev.railroadide.railroad.window;

import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.Getter;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Centralized manager for all application windows and popups.
 * Handles the primary window, sub-windows, and dialog-style popups.
 */
public class WindowManager {
    @Getter
    private final Stage primaryStage;
    @Getter
    private Scene primaryScene;

    private final List<Stage> childWindows = new ArrayList<>();

    /**
     * Initialize the WindowManager with the primary application stage.
     *
     * @param primaryStage The main application stage
     */
    public WindowManager(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    /**
     * Show the primary application window with the given scene.
     * The window size is set to 75% of the screen size by default.
     *
     * @param scene      Main content scene
     * @param title      Window title
     * @param iconStream Optional icon image stream
     */
    public void showPrimary(Scene scene, String title, InputStream iconStream) {
        this.primaryScene = scene;

        // Calculate window size based on screen
        Screen screen = Screen.getPrimary();
        double screenW = screen.getBounds().getWidth();
        double screenH = screen.getBounds().getHeight();

        double windowW = screenW * 0.75;
        double windowH = screenH * 0.75;

        primaryStage.setTitle(title);
        if (iconStream != null) {
            primaryStage.getIcons().add(new Image(iconStream));
        }

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(windowW * 0.5);
        primaryStage.setMinHeight(windowH * 0.5);
        primaryStage.setWidth(windowW);
        primaryStage.setHeight(windowH);

        primaryStage.show();
    }

    /**
     * Create and show a sub-window with the given scene.
     *
     * @param title Window title
     * @param scene Content scene
     * @param init  Optional Stage configuration (resizable, icons, etc.)
     * @return the Stage created
     */
    public Stage createSubWindow(String title, Scene scene, Consumer<Stage> init) {
        Stage stage = WindowBuilder.create()
            .title(title)
            .scene(scene)
            .owner(primaryStage)
            .modality(Modality.NONE)
            .onInit(init)
            .build();

        childWindows.add(stage);
        stage.setOnCloseRequest(e -> childWindows.remove(stage));
        return stage;
    }

    /**
     * Get a list of currently open child windows.
     *
     * @return List of child Stage instances
     */
    public List<Stage> getChildWindows() {
        return new ArrayList<>(childWindows);
    }

    /**
     * Close all currently open child windows.
     */
    public void closeAllChildWindows() {
        for (Stage stage : new ArrayList<>(childWindows)) {
            stage.close();
        }

        childWindows.clear();
    }

    /**
     * Show a blocking popup (modal window).
     *
     * @param title Window title
     * @param scene Popup content
     * @param init  Optional Stage configuration
     * @return the Stage created (already shown)
     */
    public Stage showPopup(String title, Scene scene, Consumer<Stage> init) {
        return WindowBuilder.create()
            .title(title)
            .scene(scene)
            .owner(primaryStage)
            .modality(Modality.APPLICATION_MODAL)
            .resizable(false)
            .onInit(init)
            .showImmediately(true)
            .build();
    }

    /**
     * Set the title of the primary application window.
     *
     * @param title New window title
     */
    public void setPrimaryTitle(String title) {
        primaryStage.setTitle(title);
    }

    /**
     * Set or update the icon of the primary application window.
     *
     * @param iconStream InputStream of the icon image (null to clear icons)
     */
    public void setPrimaryIcon(InputStream iconStream) {
        primaryStage.getIcons().clear();
        if (iconStream != null) {
            primaryStage.getIcons().add(new Image(iconStream));
        }
    }
}
