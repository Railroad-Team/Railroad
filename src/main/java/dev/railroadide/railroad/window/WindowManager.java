package dev.railroadide.railroad.window;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import dev.railroadide.railroad.theme.ThemeManager;
import dev.railroadide.railroad.utility.MacUtils;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3x2d;

import java.io.InputStream;
import java.util.*;

/**
 * Centralized manager for all application windows and popups.
 * Handles the primary window, sub-windows, and dialog-style popups.
 */
public class WindowManager {
    private static final String SCALE_STYLE_MARKER_START = "/* railroad-ui-scale:start */";
    private static final String SCALE_STYLE_MARKER_END = "/* railroad-ui-scale:end */";
    private static final String SCALE_VALUE_KEY = "railroad.uiScale.value";
    private static final String SCALE_LISTENER_KEY = "railroad.uiScale.listener";

    @Getter
    private Stage primaryStage;
    @Getter
    private Scene primaryScene;

    private final List<Stage> childWindows = new ArrayList<>();
    private final Map<Stage, WindowEvents> windowEventMap = new HashMap<>();

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
        if (this.primaryStage != null) {
            untrackWindowEvents(this.primaryStage);
        }

        Objects.requireNonNull(primaryStage, "Primary stage cannot be null");
        this.primaryStage = primaryStage;
        this.primaryScene = primaryStage.getScene();
        ThemeManager.apply(this.primaryScene);
        this.primaryStage.getIcons().add(AppResources.icon());
        trackWindowEvents(this.primaryStage);
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
        applyCurrentUiScale(primaryStage);
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
        applyCurrentUiScale(stage);
        trackWindowEvents(stage);
        stage.setOnCloseRequest(event -> {
            childWindows.remove(stage);
            untrackWindowEvents(stage);
        });
    }

    public WindowEvents trackWindowEvents(Stage stage) {
        var events = new WindowEvents(stage);
        windowEventMap.put(stage, events);
        events.beginTracking();
        return events;
    }

    public WindowEvents untrackWindowEvents(Stage stage) {
        WindowEvents events = windowEventMap.remove(stage);
        if (events != null) {
            events.stopTracking();
        }

        return events;
    }

    public static void toggleFullScreen() {
        Stage primaryStage = Railroad.WINDOW_MANAGER.getPrimaryStage();
        primaryStage.setFullScreen(!primaryStage.isFullScreen());
    }

    public void applyUiScaleToAllWindows(Integer newValue) {
        if (newValue == null || newValue <= 0)
            return;

        double scale = newValue / 100.0;
        if (primaryStage != null) {
            applyUiScale(primaryStage, scale);
        }

        for (Stage child : childWindows) {
            applyUiScale(child, scale);
        }
    }

    public void applyUiScale(Stage stage, double scale) {
        if (stage == null)
            return;

        Scene scene = stage.getScene();
        if (scene == null)
            return;

        ensureSceneScaleTracking(scene);
        scene.getProperties().put(SCALE_VALUE_KEY, scale);
        applyUiScale(scene, scale);
    }

    private double getCurrentScale(Scene scene) {
        Object value = scene.getProperties().get(SCALE_VALUE_KEY);
        return value instanceof Number number ? number.doubleValue() : 1.0;
    }

    private void applyCurrentUiScale(Stage stage) {
        int scalePercent = SettingsHandler.getValue(Settings.UI_SCALE);
        applyUiScale(stage, scalePercent / 100.0);
    }

    private void ensureSceneScaleTracking(Scene scene) {
        if (scene.getProperties().containsKey(SCALE_LISTENER_KEY))
            return;

        ChangeListener<Object> listener = (observable, oldValue, newValue) ->
            Platform.runLater(() -> applyUiScale(scene, getCurrentScale(scene)));
        scene.rootProperty().addListener(listener);
        scene.getProperties().put(SCALE_LISTENER_KEY, listener);
    }

    private void applyUiScale(Scene scene, double scale) {
        if (scene == null || scene.getRoot() == null)
            return;

        String style = scene.getRoot().getStyle();
        String normalizedStyle = stripUiScaleStyle(style);
        String scaleStyle = SCALE_STYLE_MARKER_START + " -fx-font-size: " + Math.round(scale * 100.0) + "%; " + SCALE_STYLE_MARKER_END;
        scene.getRoot().setStyle(normalizedStyle.isBlank() ? scaleStyle : normalizedStyle + " " + scaleStyle);
    }

    private String stripUiScaleStyle(String style) {
        if (style == null || style.isBlank())
            return "";

        int start = style.indexOf(SCALE_STYLE_MARKER_START);
        int end = style.indexOf(SCALE_STYLE_MARKER_END);
        if (start >= 0 && end >= start) {
            String prefix = style.substring(0, start).trim();
            String suffix = style.substring(end + SCALE_STYLE_MARKER_END.length()).trim();
            if (prefix.isEmpty())
                return suffix;

            if (suffix.isEmpty())
                return prefix;

            return prefix + " " + suffix;
        }

        return style.trim();
    }
}
