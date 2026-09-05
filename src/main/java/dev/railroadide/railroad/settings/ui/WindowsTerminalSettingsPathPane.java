package dev.railroadide.railroad.settings.ui;

import dev.railroadide.railroad.ui.BrowseButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRTextField;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Input pane for selecting the Windows Terminal settings file.
 */
public class WindowsTerminalSettingsPathPane extends RRHBox {
    private final ObjectProperty<Path> settingsPath = new SimpleObjectProperty<>();

    private final RRTextField pathField = new RRTextField();
    private final BrowseButton browseButton = new BrowseButton();

    /**
     * Creates a Windows Terminal settings path selector.
     *
     * @param path initial settings file path, or {@code null} when no path is configured
     */
    public WindowsTerminalSettingsPathPane(@Nullable Path path) {
        getStyleClass().add("windows-terminal-settings-path-pane");
        browseButton.textFieldProperty().set(pathField);
        browseButton.browseTypeProperty().set(BrowseButton.BrowseType.FILE);
        browseButton.defaultLocationProperty()
            .set(path != null ? path.getParent() : Path.of(System.getProperty("user.home")));
        browseButton.selectionModeProperty().set(BrowseButton.BrowseSelectionMode.SINGLE);
        browseButton.parentWindowProperty().bind(sceneProperty().flatMap(Scene::windowProperty));

        pathField.setLocalizedPlaceholder(
            "railroad.settings.appearance.terminal.windows_terminal_settings_path.placeholder");
        settingsPath
            .addListener((_, _, newValue) -> pathField.setText(newValue == null ? "" : Objects.toString(newValue)));
        pathField.textProperty().addListener((_, _, newText) -> {
            if (newText == null || newText.isBlank()) {
                setSettingsPath(null);
            } else {
                try {
                    setSettingsPath(Path.of(newText));
                } catch (Exception exception) {
                    setSettingsPath(null);
                }
            }
        });

        getChildren().addAll(pathField, browseButton);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        setSettingsPath(path);
    }

    /**
     * Returns the selected Windows Terminal settings path.
     *
     * @return the selected settings path, or {@code null} when none is set
     */
    public Path getSettingsPath() {
        return settingsPath.get();
    }

    /**
     * Returns the observable property containing the settings path.
     *
     * @return the settings path property
     */
    public ObjectProperty<Path> settingsPathProperty() {
        return settingsPath;
    }

    /**
     * Sets the Windows Terminal settings path displayed by this pane.
     *
     * @param settingsPath settings file path, or {@code null} to clear it
     */
    public void setSettingsPath(Path settingsPath) {
        this.settingsPath.set(settingsPath);
    }
}
