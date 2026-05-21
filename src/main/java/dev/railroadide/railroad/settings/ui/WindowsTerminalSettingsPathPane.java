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

public class WindowsTerminalSettingsPathPane extends RRHBox {
    private final ObjectProperty<Path> settingsPath = new SimpleObjectProperty<>();

    private final RRTextField pathField = new RRTextField();
    private final BrowseButton browseButton = new BrowseButton();

    public WindowsTerminalSettingsPathPane(@Nullable Path path) {
        getStyleClass().add("windows-terminal-settings-path-pane");
        browseButton.textFieldProperty().set(pathField);
        browseButton.browseTypeProperty().set(BrowseButton.BrowseType.FILE);
        browseButton.defaultLocationProperty().set(path != null ? path.getParent() : Path.of(System.getProperty("user.home")));
        browseButton.selectionModeProperty().set(BrowseButton.BrowseSelectionMode.SINGLE);
        browseButton.parentWindowProperty().bind(sceneProperty().flatMap(Scene::windowProperty));

        pathField.setLocalizedPlaceholder("railroad.settings.appearance.terminal.windows_terminal_settings_path.placeholder");
        settingsPath.addListener((observable, oldValue, newValue) ->
            pathField.setText(newValue == null ? "" : Objects.toString(newValue)));
        pathField.textProperty().addListener((obs, oldText, newText) -> {
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

    public Path getSettingsPath() {
        return settingsPath.get();
    }

    public ObjectProperty<Path> settingsPathProperty() {
        return settingsPath;
    }

    public void setSettingsPath(Path settingsPath) {
        this.settingsPath.set(settingsPath);
    }
}
