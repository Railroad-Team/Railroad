package dev.railroadide.railroad.theme;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class ThemeManager {
    private static boolean debug = false;
    private static String currentTheme;
    private static String baseCss;
    private static final List<String> COMPONENTS_CSS = new ArrayList<>();
    private static String debugCss;

    private static final Set<Scene> TRACKED_SCENES = Collections.synchronizedSet(new HashSet<>());

    public static void init() {
        baseCss = get("styles/base.css");
        debugCss = get("styles/debug.css");

        try {
            COMPONENTS_CSS.addAll(getComponentCssFiles());
        } catch (IOException | URISyntaxException exception) {
            Railroad.LOGGER.error("Failed to load component CSS files", exception);
        }

        currentTheme = SettingsHandler.getValue(Settings.THEME);
    }

    private static String get(String path) {
        return AppResources.getResource(path).toExternalForm();
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
        if(theme == null) theme = "";

        currentTheme = theme;
        reloadAll();
    }

    public static String getTheme() {
        return currentTheme;
    }

    public static void reloadAll() {
        Platform.runLater(() -> {
            synchronized (TRACKED_SCENES) {
                COMPONENTS_CSS.clear();

                try {
                    COMPONENTS_CSS.addAll(getComponentCssFiles());
                } catch (IOException | URISyntaxException exception) {
                    Railroad.LOGGER.error("Failed to load component CSS files", exception);
                }

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
        scene.getStylesheets().addAll(COMPONENTS_CSS);

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

    private static List<String> getComponentCssFiles() throws URISyntaxException, IOException {
        final List<String> componentCss = new ArrayList<>();
        String folderPath = "assets/railroad/styles/components";

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(folderPath);

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();

            if (url.getProtocol().equals("file")) {
                try (Stream<Path> walk = Files.walk(Paths.get(url.toURI()), 1)) {
                    walk.filter(Files::isRegularFile)
                            .forEach(p -> componentCss.add(get("styles/components/" + p.getFileName().toString())));
                }
            } else if (url.getProtocol().equals("jar")) {
                String jarPath = url.getPath().substring(5, url.getPath().indexOf("!"));
                try (JarFile jarFile = new JarFile(jarPath)) {
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith(folderPath) && !name.equals(folderPath) && !entry.isDirectory()) {
                            componentCss.add(get("styles/components/" + name.substring(folderPath.length() + 1)));
                        }
                    }
                }
            }
        }

        return componentCss;
    }
}
