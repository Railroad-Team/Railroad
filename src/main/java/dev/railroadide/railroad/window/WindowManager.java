package dev.railroadide.railroad.window;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.theme.ThemeManager;
import dev.railroadide.railroad.utility.MacUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2d;
import org.joml.Vector4d;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Centralized manager for all application windows and popups.
 * Handles the primary window, sub-windows, and dialog-style popups.
 */
public class WindowManager {
    @Getter
    private Stage primaryStage;
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

    public WindowManager() {
    }

    public void setPrimaryStage(@NotNull Stage primaryStage) {
        Objects.requireNonNull(primaryStage, "Primary stage cannot be null");
        this.primaryStage = primaryStage;
        this.primaryScene = primaryStage.getScene();
        ThemeManager.apply(this.primaryScene);
        this.primaryStage.getIcons().add(AppResources.icon());
    }

    /**
     * Show the primary application window with the given scene.
     * The window size is set to 75% of the screen size by default.
     *
     * @param scene Main content scene
     * @param title Window title
     */
    public void showPrimary(Stage primaryStage, Scene scene, String title) {
        this.primaryScene = scene;

        primaryStage.setScene(this.primaryScene);
        primaryStage.setTitle(title);
        applyPreferredSize(primaryStage);
        setPrimaryStage(primaryStage);

        // Create a MacOS specific Menu Bar and Application Menu
        MacUtils.initialize();

        primaryStage.show();

        // Show the MacOS specific menu bar
        MacUtils.show(primaryStage);
    }

    /**
     * Apply the preferred window size to the given stage.
     *
     * @param stage Stage to apply sizes to
     */
    public void applyPreferredSize(Stage stage) {
        Matrix3x2d windowSizes = calculatePreferredWindowSize();
        stage.setMinWidth(windowSizes.m00());
        stage.setMinHeight(windowSizes.m01());
        stage.setWidth(windowSizes.m10());
        stage.setHeight(windowSizes.m11());
        stage.setMaxWidth(windowSizes.m20());
        stage.setMaxHeight(windowSizes.m21());
    }

    /**
     * Calculate the preferred window size based on 75% of the primary screen dimensions.
     *
     * @return {@link Matrix3x2d} containing (minWidth, minHeight, preferredWidth, preferredHeight, maxWidth, maxHeight)
     */
    public Matrix3x2d calculatePreferredWindowSize() {
        Screen screen = Screen.getPrimary();
        double screenW = screen.getBounds().getWidth();
        double screenH = screen.getBounds().getHeight();

        double windowW = screenW * 0.75;
        double windowH = screenH * 0.75;

        double minWindowW = windowW * 0.5;
        double minWindowH = windowH * 0.5;

        return new Matrix3x2d(minWindowW, minWindowH, windowW, windowH, screenW, screenH);
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

    public void registerChildWindow(Stage stage) {
        childWindows.add(stage);
        stage.setOnCloseRequest(event -> childWindows.remove(stage));
    }

    public static void toggleFullScreen() {
        Stage primaryStage = Railroad.WINDOW_MANAGER.getPrimaryStage();
        primaryStage.setFullScreen(!primaryStage.isFullScreen());
    }
}
