package io.github.railroad.ui;

import io.github.railroad.ui.localized.LocalizedButton;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;

public class BrowseButton extends LocalizedButton {
    private final ObjectProperty<Window> parentWindow = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> textField = new SimpleObjectProperty<>();
    private final ObjectProperty<BrowseType> browseType = new SimpleObjectProperty<>(BrowseType.FILE);
    private final ObjectProperty<BrowseSelectionMode> selectionMode = new SimpleObjectProperty<>(BrowseSelectionMode.SINGLE);
    private final ObjectProperty<Path> defaultLocation = new SimpleObjectProperty<>(Path.of(System.getProperty("user.home")));

    public BrowseButton() {
        super("railroad.home.project.browse");
        setOnAction(event -> {
            TextField textField = this.textField.getValue();
            if (textField == null)
                return;

            BrowseSelectionMode selectionMode = this.selectionMode.getValue();
            if (selectionMode == null)
                selectionMode = BrowseSelectionMode.SINGLE;

            Path defaultLocation = this.defaultLocation.getValue();
            if (defaultLocation == null)
                defaultLocation = Path.of(System.getProperty("user.home"));

            switch (browseType.getValue()) {
                case FILE -> {
                    var fileChooser = fileBrowser(defaultLocation.toFile(), "Select File", null);
                    if (selectionMode == BrowseSelectionMode.SINGLE) {
                        textField.setText(fileChooser.showOpenDialog(parentWindow.get()).getAbsolutePath());
                    } else {
                        textField.setText(fileChooser.showOpenMultipleDialog(parentWindow.get()).stream()
                                .map(File::getAbsolutePath)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse(""));
                    }
                }
                case DIRECTORY -> {
                    var folderBrowser = folderBrowser(defaultLocation.toFile(), "Select Directory");
                    textField.setText(folderBrowser.showDialog(parentWindow.get()).getAbsolutePath());
                }
                case IMAGE -> {
                    var imageChooser = imageBrowser(defaultLocation.toFile(), "Select Image");
                    if (selectionMode == BrowseSelectionMode.SINGLE) {
                        textField.setText(imageChooser.showOpenDialog(parentWindow.get()).getAbsolutePath());
                    } else {
                        textField.setText(imageChooser.showOpenMultipleDialog(parentWindow.get()).stream()
                                .map(File::getAbsolutePath)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse(""));
                    }
                }
            }
        });
    }

    public static DirectoryChooser folderBrowser(File defaultPath, String title) {
        var directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);
        directoryChooser.setInitialDirectory(defaultPath);

        return directoryChooser;
    }

    public static FileChooser fileBrowser(File defaultPath, String title, @Nullable FileChooser.ExtensionFilter filter) {
        var fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.setInitialDirectory(defaultPath);
        if (filter != null) {
            fileChooser.setSelectedExtensionFilter(filter);
        }

        return fileChooser;
    }

    public static FileChooser imageBrowser(File defaultPath, String title) {
        var filter = new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif");

        var fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.setInitialDirectory(defaultPath);
        fileChooser.setSelectedExtensionFilter(filter);

        return fileChooser;
    }

    public ObjectProperty<Window> parentWindowProperty() {
        return parentWindow;
    }

    public ObjectProperty<TextField> textFieldProperty() {
        return textField;
    }

    public ObjectProperty<BrowseType> browseTypeProperty() {
        return browseType;
    }

    public ObjectProperty<BrowseSelectionMode> selectionModeProperty() {
        return selectionMode;
    }

    public ObjectProperty<Path> defaultLocationProperty() {
        return defaultLocation;
    }

    public enum BrowseType {
        FILE,
        DIRECTORY,
        IMAGE
    }

    public enum BrowseSelectionMode {
        SINGLE,
        MULTIPLE
    }
}
