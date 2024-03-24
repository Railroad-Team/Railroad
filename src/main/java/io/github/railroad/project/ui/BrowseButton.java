package io.github.railroad.project.ui;

import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;

public class BrowseButton extends Button {
    private final TextField textField;
    private final BrowseType browseType;
    private final BrowseSelectionMode selectionMode;

    public BrowseButton(TextField textField, BrowseType browseType, BrowseSelectionMode selectionMode) {
        this.textField = textField;
        this.browseType = browseType;
        this.selectionMode = selectionMode;

        setText("Browse");
        setOnAction(event -> {
            switch (browseType) {
                case FILE -> {
                    var fileChooser = new FileChooser();
                    fileChooser.setTitle("Select File");
                    if (selectionMode == BrowseSelectionMode.SINGLE) {
                        textField.setText(fileChooser.showOpenDialog(null).getAbsolutePath());
                    } else {
                        textField.setText(fileChooser.showOpenMultipleDialog(null).stream()
                                .map(File::getAbsolutePath)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse(""));
                    }
                }
                case DIRECTORY -> {
                    var directoryChooser = new DirectoryChooser();
                    directoryChooser.setTitle("Select Directory");
                    textField.setText(directoryChooser.showDialog(null).getAbsolutePath());
                }
                case IMAGE -> {
                    var fileChooser = new FileChooser();
                    fileChooser.setTitle("Select Image");
                    fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif"));
                    if (selectionMode == BrowseSelectionMode.SINGLE) {
                        textField.setText(fileChooser.showOpenDialog(null).getAbsolutePath());
                    } else {
                        textField.setText(fileChooser.showOpenMultipleDialog(null).stream()
                                .map(File::getAbsolutePath)
                                .reduce((a, b) -> a + ", " + b)
                                .orElse(""));
                    }
                }
            }
        });
    }

    public TextField getTextField() {
        return this.textField;
    }

    public BrowseType getBrowseType() {
        return this.browseType;
    }

    public BrowseSelectionMode getSelectionMode() {
        return this.selectionMode;
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
