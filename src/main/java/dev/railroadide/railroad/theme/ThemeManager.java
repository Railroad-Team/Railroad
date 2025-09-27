package dev.railroadide.railroad.theme;

import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ThemeManager {
    private static boolean debug = false;
    private static String currentTheme;
    private static String baseCss;
    private static String componentsCss;
    private static String debugCss;
    private static String codeAreaCss;

    private static final Set<Scene> TRACKED_SCENES = Collections.synchronizedSet(new HashSet<>());

    public static void init() {
        baseCss = get("styles/base.css");
        componentsCss = get("styles/components.css");
        debugCss = get("styles/debug.css");
        codeAreaCss = get("styles/code-area.css");

        currentTheme = SettingsHandler.getValue(Settings.THEME);
    }

    private static String get(String path) {
        return Railroad.getResource(path).toExternalForm();
    }

    public static void apply(Scene scene) {
        if (scene == null) return;

        TRACKED_SCENES.add(scene);
        applyThemeToScene(scene);

        scene.setOnKeyReleased(event -> {
            if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.D) {
                toggleDebug(scene);
            } else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.R) {
                reloadAll();
            }
        });
    }

    public static void setTheme(String theme) {
        currentTheme = theme;
        reloadAll();
    }

    public static String getTheme() {
        return currentTheme;
    }

    public static void reloadAll() {
        Platform.runLater(() -> {
            synchronized (TRACKED_SCENES) {
                for (Scene scene : TRACKED_SCENES) {
                    applyThemeToScene(scene);
                }
            }
        });
    }

    private static void applyThemeToScene(Scene scene) {
        scene.getStylesheets().clear();

        if (currentTheme != null && !currentTheme.isEmpty()) {
            if (currentTheme.startsWith("default")) {
                scene.getStylesheets().add(get("styles/" + currentTheme + ".css"));
            } else {
                scene.getStylesheets().add(
                    new File(ThemeDownloadManager.getThemesDirectory()
                        + "/" + currentTheme + ".css").toURI().toString()
                );
            }
        }

        scene.getStylesheets().add(baseCss);
        scene.getStylesheets().add(componentsCss);
        scene.getStylesheets().add(codeAreaCss);

        if (debug) {
            scene.getStylesheets().add(debugCss);
        }
    }

    private static void toggleDebug(Scene scene) {
        if (debug) {
            scene.getStylesheets().remove(debugCss);
        } else {
            scene.getStylesheets().add(debugCss);
        }

        debug = !debug;
    }
}
