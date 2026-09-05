package dev.railroadide.railroad.theme;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.handler.SettingsHandler;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Manages the application theme and applies it to JavaFX scenes.
 */
public class ThemeManager {
    private static boolean debug = false;

    private static final StringProperty currentTheme = new SimpleStringProperty();

    private static String baseCss;
    private static final List<String> COMPONENTS_CSS = new ArrayList<>();
    private static String debugCss;

    private static final Set<Scene> TRACKED_SCENES = Collections.synchronizedSet(new HashSet<>());

    /**
     * Initializes the ThemeManager by loading base and debug CSS files, as well as component CSS files.
     * Sets up a listener to reload all scenes when the theme changes.
     */
    public static void init() {
        baseCss = getAsExternalForm("styles/base.css");
        debugCss = getAsExternalForm("styles/debug.css");

        try {
            COMPONENTS_CSS.addAll(getComponentCssFiles());
        } catch (IOException | URISyntaxException exception) {
            Railroad.LOGGER.error("Failed to load component CSS files", exception);
        }

        currentTheme.addListener(_ -> reloadAll());
        currentTheme.set(SettingsHandler.getValue(Settings.THEME));
    }

    /**
     * Converts a resource path to its external form URL.
     *
     * @param path the resource path
     * @return the external form URL of the resource
     */
    public static String getAsExternalForm(String path) {
        return AppResources.getResource(path).toExternalForm();
    }

    /**
     * Applies the current theme to the given JavaFX scene and sets up key event handlers for debug toggling and theme reloading.
     *
     * @param scene the JavaFX scene to apply the theme to
     */
    public static void apply(Scene scene) {
        if (scene == null)
            return;

        TRACKED_SCENES.add(scene);
        applyThemeToScene(currentTheme.get(), scene);

        scene.setOnKeyReleased(event -> {
            if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.D) {
                toggleDebug(scene);
            } else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.R) {
                reloadAll();
            }
        });
    }

    /**
     * Prepares a scene transition by applying the stylesheets from the previous scene to the next scene.
     *
     * @param previousScene the previous JavaFX scene
     * @param nextScene     the next JavaFX scene
     */
    public static void prepareSceneTransition(Scene previousScene, Scene nextScene) {
        if (nextScene == null)
            return;

        if (previousScene != null) {
            nextScene.getStylesheets().setAll(previousScene.getStylesheets());
        }

        apply(nextScene);
    }

    /**
     * Releases the resources associated with a JavaFX scene, removing it from tracking and clearing its key event handlers.
     *
     * @param scene the JavaFX scene to release
     */
    public static void release(Scene scene) {
        if (scene == null)
            return;

        TRACKED_SCENES.remove(scene);
        scene.setOnKeyReleased(null);
    }

    /**
     * Returns the current theme property.
     *
     * @return the current theme property
     */
    public static StringProperty getCurrentThemeProperty() {
        return currentTheme;
    }

    /**
     * Sets the current theme to the specified value. If the provided theme is null, it defaults to an empty string.
     *
     * @param theme the new theme to set
     */
    public static void setTheme(String theme) {
        if (theme == null) {
            theme = "";
        }

        currentTheme.set(theme);
    }

    /**
     * Returns the current theme as a string.
     *
     * @return the current theme
     */
    public static String getTheme() {
        return currentTheme.get();
    }

    /**
     * Reloads all tracked scenes with the current theme and component CSS files.
     * This method is executed on the JavaFX application thread.
     */
    // TODO: This doesn't work, everything explodes
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
                    applyThemeToScene(currentTheme.get(), scene);
                }
            }
        });
    }

    /**
     * Applies the specified theme to the given JavaFX scene, clearing existing stylesheets and adding the base and component CSS files.
     *
     * @param theme the theme to apply
     * @param scene the JavaFX scene to apply the theme to
     */
    public static void applyThemeToScene(String theme, Scene scene) {
        ResponsiveDesign.install(scene);
        scene.getStylesheets().clear();

        if (theme != null && !theme.isEmpty()) {
            if (theme.startsWith("default")) {
                scene.getStylesheets().add(getAsExternalForm("styles/" + theme + ".css"));
            } else {
                scene.getStylesheets().add(
                    new File(ThemeDownloadManager.getThemesDirectory()
                        + "/" + theme + ".css").toURI().toString());
            }
        }

        scene.getStylesheets().add(baseCss);
        scene.getStylesheets().addAll(COMPONENTS_CSS);

        if (debug) {
            scene.getStylesheets().add(debugCss);
        }
    }

    /**
     * Toggles the debug mode for the given JavaFX scene, adding or removing the debug CSS stylesheet.
     *
     * @param scene the JavaFX scene to toggle debug mode for
     */
    public static void toggleDebug(Scene scene) {
        if (debug) {
            scene.getStylesheets().remove(debugCss);
        } else {
            scene.getStylesheets().add(debugCss);
        }

        debug = !debug;
    }

    /**
     * Retrieves a list of component CSS files from the specified folder path, handling both file and JAR protocols.
     *
     * @return a list of component CSS file paths in external form
     * @throws URISyntaxException if the URL syntax is incorrect
     * @throws IOException        if an I/O error occurs while accessing the resources
     */
    public static List<String> getComponentCssFiles() throws URISyntaxException, IOException {
        final List<String> componentCss = new ArrayList<>();
        String folderPath = "assets/railroad/styles/components";

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(folderPath);

        while (resources.hasMoreElements()) {
            URL url = resources.nextElement();

            if (url.getProtocol().equals("file")) {
                try (Stream<Path> walk = Files.walk(Paths.get(url.toURI()), 1)) {
                    walk.filter(Files::isRegularFile)
                        .forEach(p -> componentCss
                            .add(getAsExternalForm("styles/components/" + p.getFileName().toString())));
                }
            } else if (url.getProtocol().equals("jar")) {
                JarURLConnection connection = (JarURLConnection) url.openConnection();
                try (JarFile jarFile = connection.getJarFile()) {
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.startsWith(folderPath) && !name.equals(folderPath) && !entry.isDirectory()) {
                            componentCss
                                .add(getAsExternalForm("styles/components/" + name.substring(folderPath.length() + 1)));
                        }
                    }
                }
            }
        }

        return componentCss;
    }
}
