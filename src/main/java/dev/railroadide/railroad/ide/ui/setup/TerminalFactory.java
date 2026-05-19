package dev.railroadide.railroad.ide.ui.setup;

import com.google.gson.JsonObject;
import com.kodedu.terminalfx.Terminal;
import com.kodedu.terminalfx.TerminalBuilder;
import com.kodedu.terminalfx.config.TerminalConfig;
import com.kodedu.terminalfx.helper.ThreadHelper;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.settings.Settings;
import dev.railroadide.railroad.settings.TerminalFontMode;
import dev.railroadide.railroad.utility.OperatingSystem;
import dev.railroadide.railroad.utility.ShutdownHooks;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.*;

/**
 * Creates configured TerminalFX instances pointing at a specific project path.
 */
public final class TerminalFactory {
    private static final String DEFAULT_FONT_STACK = "\"Cascadia Mono\", \"JetBrains Mono\", \"Consolas\", monospace";
    private static final Set<Terminal> OPEN_TERMINALS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final List<String> WINDOWS_TERMINAL_PACKAGE_NAMES = List.of(
        "Microsoft.WindowsTerminal_8wekyb3d8bbwe",
        "Microsoft.WindowsTerminalPreview_8wekyb3d8bbwe",
        "Microsoft.WindowsTerminalDev_8wekyb3d8bbwe",
        "Microsoft.WindowsTerminalCanary_8wekyb3d8bbwe"
    );

    private TerminalFactory() {
    }

    static {
        Settings.TERMINAL_FONT_MODE.addListener((oldValue, newValue) -> refreshOpenTerminals());
        Settings.TERMINAL_INSTALLED_FONT.addListener((oldValue, newValue) -> refreshOpenTerminals());
        Settings.TERMINAL_CUSTOM_FONT_FAMILY.addListener((oldValue, newValue) -> refreshOpenTerminals());
        Settings.WINDOWS_TERMINAL_SETTINGS_PATH.addListener((oldValue, newValue) -> refreshOpenTerminals());
        ShutdownHooks.addHook(TerminalFactory::shutdownOpenTerminals);
    }

    public static Terminal create(Path path) {
        var terminalConfig = createTerminalConfig();
        var terminalBuilder = new TerminalBuilder(terminalConfig);
        terminalBuilder.setTerminalPath(path);
        Terminal terminal = terminalBuilder.newTerminal().getTerminal();
        registerTerminal(terminal);
        return terminal;
    }

    public static TerminalConfig createTerminalConfig() {
        var terminalConfig = new TerminalConfig();
        terminalConfig.setBackgroundColor(Color.rgb(16, 16, 16));
        terminalConfig.setForegroundColor(Color.rgb(240, 240, 240));
        terminalConfig.setCursorColor(Color.rgb(255, 0, 0, 0.5));
        terminalConfig.setFontFamily(resolveTerminalFontFamily());

        if (OperatingSystem.isWindows()) {
            terminalConfig.setWindowsTerminalStarter(
                "powershell.exe -NoLogo -NoExit -Command " +
                    "\"[Console]::InputEncoding=[System.Text.UTF8Encoding]::new($false); " +
                    "[Console]::OutputEncoding=[System.Text.UTF8Encoding]::new($false); " +
                    "chcp 65001 > $null\""
            );
        }

        return terminalConfig;
    }

    private static void refreshOpenTerminals() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(TerminalFactory::refreshOpenTerminals);
            return;
        }

        TerminalConfig terminalConfig = createTerminalConfig();
        Set<Terminal> terminals = new LinkedHashSet<>();
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (scene == null || scene.getRoot() == null)
                continue;

            collectTerminals(scene.getRoot(), terminals);
        }

        for (Terminal terminal : terminals) {
            terminal.updatePrefs(terminalConfig);
        }
    }

    private static void collectTerminals(Node node, Set<Terminal> terminals) {
        if (node instanceof Terminal terminal) {
            terminals.add(terminal);
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectTerminals(child, terminals);
            }
        }
    }

    private static void shutdownOpenTerminals() {
        Set<Terminal> terminals = new LinkedHashSet<>(OPEN_TERMINALS);
        collectVisibleTerminals(terminals);

        for (Terminal terminal : terminals) {
            try {
                if (terminal.getProcess() != null) {
                    terminal.getProcess().destroy();
                }
            } catch (Exception exception) {
                Railroad.LOGGER.debug("Failed to destroy terminal process cleanly", exception);
            }

            closeQuietly(terminal.getInputReader());
            closeQuietly(terminal.getErrorReader());
            closeQuietly(terminal.getOutputWriter());
        }

        OPEN_TERMINALS.clear();
        ThreadHelper.stopExecutorService();
    }

    private static void registerTerminal(Terminal terminal) {
        OPEN_TERMINALS.add(terminal);

        terminal.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null)
                return;

            registerWindowListener(terminal, newScene.getWindow());
            newScene.windowProperty().addListener((sceneObs, oldWindow, newWindow) ->
                registerWindowListener(terminal, newWindow));
        });
    }

    private static void registerWindowListener(Terminal terminal, Window window) {
        if (window == null)
            return;

        window.showingProperty().addListener((windowObs, wasShowing, isShowing) -> {
            if (!isShowing) {
                closeTerminal(terminal);
            }
        });
    }

    private static void collectVisibleTerminals(Set<Terminal> terminals) {
        for (Window window : Window.getWindows()) {
            Scene scene = window.getScene();
            if (scene == null || scene.getRoot() == null)
                continue;

            collectTerminals(scene.getRoot(), terminals);
        }
    }

    private static void closeTerminal(Terminal terminal) {
        if (terminal == null)
            return;

        try {
            if (terminal.getProcess() != null) {
                terminal.getProcess().destroy();
            }
        } catch (Exception exception) {
            Railroad.LOGGER.debug("Failed to destroy terminal process cleanly", exception);
        }

        closeQuietly(terminal.getInputReader());
        closeQuietly(terminal.getErrorReader());
        closeQuietly(terminal.getOutputWriter());
        OPEN_TERMINALS.remove(terminal);
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null)
            return;

        try {
            closeable.close();
        } catch (Exception exception) {
            Railroad.LOGGER.debug("Failed to close terminal resource cleanly", exception);
        }
    }

    private static String resolveTerminalFontFamily() {
        TerminalFontMode mode = Settings.TERMINAL_FONT_MODE.getOrDefaultValue();
        if (mode == TerminalFontMode.INSTALLED_FONT) {
            String installedFont = Settings.TERMINAL_INSTALLED_FONT.getValue();
            if (installedFont != null && !installedFont.isBlank())
                return "\"" + installedFont.replace("\"", "\\\"") + "\", " + DEFAULT_FONT_STACK;
        }

        if (mode == TerminalFontMode.CUSTOM_FAMILY) {
            String customFamily = Settings.TERMINAL_CUSTOM_FONT_FAMILY.getValue();
            if (customFamily != null && !customFamily.isBlank())
                return customFamily;
        }

        if (!OperatingSystem.isWindows())
            return DEFAULT_FONT_STACK;

        return findWindowsTerminalFontFace()
            .map(fontFace -> "\"" + fontFace.replace("\"", "\\\"") + "\", " + DEFAULT_FONT_STACK)
            .orElse(DEFAULT_FONT_STACK);
    }

    private static Optional<String> findWindowsTerminalFontFace() {
        for (Path settingsPath : getWindowsTerminalSettingsCandidates()) {
            try {
                if (Files.notExists(settingsPath))
                    continue;

                String content = Files.readString(settingsPath);
                JsonObject root = Railroad.GSON.fromJson(content, JsonObject.class);
                if (root == null || !root.has("profiles") || !root.get("profiles").isJsonObject())
                    continue;

                JsonObject profiles = root.getAsJsonObject("profiles");
                String defaultProfileId = root.has("defaultProfile") ? root.get("defaultProfile").getAsString() : null;

                Optional<String> profileFont = findDefaultProfileFontFace(profiles, defaultProfileId);
                if (profileFont.isPresent())
                    return profileFont;

                Optional<String> defaultsFont = findFontFace(profiles.getAsJsonObject("defaults"));
                if (defaultsFont.isPresent())
                    return defaultsFont;
            } catch (Exception exception) {
                Railroad.LOGGER.debug("Failed to resolve Windows Terminal font settings from {}", settingsPath, exception);
            }
        }

        return Optional.empty();
    }

    private static List<Path> getWindowsTerminalSettingsCandidates() {
        Set<Path> candidates = new LinkedHashSet<>();

        Path userOverride = Settings.WINDOWS_TERMINAL_SETTINGS_PATH.getValue();
        if (userOverride != null) {
            candidates.add(userOverride);
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank())
            return List.copyOf(candidates);

        Path localAppDataPath = Path.of(localAppData);
        Path packagesPath = localAppDataPath.resolve("Packages");
        for (String packageName : WINDOWS_TERMINAL_PACKAGE_NAMES) {
            candidates.add(packagesPath.resolve(packageName).resolve("LocalState").resolve("settings.json"));
        }

        candidates.add(localAppDataPath.resolve("Microsoft").resolve("Windows Terminal").resolve("settings.json"));
        candidates.add(localAppDataPath.resolve("Microsoft").resolve("Windows Terminal Preview").resolve("settings.json"));

        candidates.addAll(findInstalledWindowsTerminalPackageSettings(packagesPath));

        return List.copyOf(candidates);
    }

    private static List<Path> findInstalledWindowsTerminalPackageSettings(Path packagesPath) {
        List<Path> candidates = new ArrayList<>();
        if (Files.notExists(packagesPath))
            return candidates;

        try (var children = Files.list(packagesPath)) {
            children
                .filter(Files::isDirectory)
                .filter(path -> path.getFileName().toString().startsWith("Microsoft.WindowsTerminal"))
                .map(path -> path.resolve("LocalState").resolve("settings.json"))
                .forEach(candidates::add);
        } catch (IOException exception) {
            Railroad.LOGGER.debug("Failed to scan Windows Terminal package settings under {}", packagesPath, exception);
        }

        return candidates;
    }

    private static Optional<String> findDefaultProfileFontFace(JsonObject profiles, String defaultProfileId) {
        if (defaultProfileId == null || !profiles.has("list") || !profiles.get("list").isJsonArray())
            return Optional.empty();

        for (var element : profiles.getAsJsonArray("list")) {
            if (!element.isJsonObject())
                continue;

            JsonObject profile = element.getAsJsonObject();
            if (!profile.has("guid") || !defaultProfileId.equals(profile.get("guid").getAsString()))
                continue;

            return findFontFace(profile);
        }

        return Optional.empty();
    }

    private static Optional<String> findFontFace(JsonObject profile) {
        if (profile == null || !profile.has("font") || !profile.get("font").isJsonObject())
            return Optional.empty();

        JsonObject font = profile.getAsJsonObject("font");
        if (!font.has("face"))
            return Optional.empty();

        String face = font.get("face").getAsString();
        return face == null || face.isBlank() ? Optional.empty() : Optional.of(face);
    }
}
