package io.github.railroad.project.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.Button;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;

public class BrowseButton extends Button {
    private final ObjectProperty<Window> parentWindow = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> textField = new SimpleObjectProperty<>();
    private final ObjectProperty<BrowseType> browseType = new SimpleObjectProperty<>(BrowseType.FILE);
    private final ObjectProperty<BrowseSelectionMode> selectionMode = new SimpleObjectProperty<>(BrowseSelectionMode.SINGLE);
    private final ObjectProperty<Path> defaultLocation = new SimpleObjectProperty<>(Path.of(System.getProperty("user.home")));

    public BrowseButton() {
        setText("Browse");
        setOnAction(event -> {
            TextField textField = this.textField.getValue();
            if(textField == null)
                return;

            BrowseSelectionMode selectionMode = this.selectionMode.getValue();
            if (selectionMode == null)
                selectionMode = BrowseSelectionMode.SINGLE;

            Path defaultLocation = this.defaultLocation.getValue();
            if(defaultLocation == null)
                defaultLocation = Path.of(System.getProperty("user.home"));

            switch (browseType.getValue()) {
                case FILE -> {
                    var fileChooser = new FileChooser();
                    fileChooser.setTitle("Select File");
                    fileChooser.setInitialDirectory(defaultLocation.toFile());
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
                    var directoryChooser = new DirectoryChooser();
                    directoryChooser.setTitle("Select Directory");
                    directoryChooser.setInitialDirectory(defaultLocation.toFile());
                    textField.setText(directoryChooser.showDialog(parentWindow.get()).getAbsolutePath());
                }
                case IMAGE -> {
                    var fileChooser = new FileChooser();
                    fileChooser.setTitle("Select Image");
                    fileChooser.setInitialDirectory(defaultLocation.toFile());
                    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));
                    if (selectionMode == BrowseSelectionMode.SINGLE) {
                        textField.setText(fileChooser.showOpenDialog(parentWindow.get()).getAbsolutePath());
                    } else {
                        textField.setText(fileChooser.showOpenMultipleDialog(parentWindow.get()).stream()
                                .map(File::getAbsolutePath)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse(""));
                    }
                }
            }
        });
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
