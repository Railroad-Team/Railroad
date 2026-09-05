package dev.railroadide.railroad.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * A button that opens a file or directory browser dialog.
 * It allows users to select files, directories, or images and updates a TextField with the selected path(s).
 */
public class BrowseButton extends RRButton {
    private final ObjectProperty<Window> parentWindow = new SimpleObjectProperty<>();
    private final ObjectProperty<TextField> textField = new SimpleObjectProperty<>();
    private final ObjectProperty<BrowseType> browseType = new SimpleObjectProperty<>(BrowseType.FILE);
    private final ObjectProperty<BrowseSelectionMode> selectionMode = new SimpleObjectProperty<>(
        BrowseSelectionMode.SINGLE);
    private final ObjectProperty<Path> defaultLocation = new SimpleObjectProperty<>(
        Path.of(System.getProperty("user.home")));

    /**
     * Creates a localized browse button configured for a single file in the user's home directory.
     * Set {@link #textFieldProperty()} to receive selected paths; cancellation leaves its text unchanged.
     */
    public BrowseButton() {
        super("railroad.generic.browse");
        setOnAction(event -> {
            TextField textField = this.textField.getValue();
            if (textField == null)
                return;

            BrowseSelectionMode selectionMode = this.selectionMode.getValue();
            if (selectionMode == null) {
                selectionMode = BrowseSelectionMode.SINGLE;
            }

            Path defaultLocation = this.defaultLocation.getValue();
            if (defaultLocation == null) {
                defaultLocation = Path.of(System.getProperty("user.home"));
            }

            switch (browseType.getValue()) {
                case FILE -> {
                    var fileChooser = fileBrowser(defaultLocation.toFile(), "Select File", null);
                    if (selectionMode == BrowseSelectionMode.SINGLE) {
                        File file = fileChooser.showOpenDialog(parentWindow.get());
                        if (file == null)
                            return;

                        textField.setText(file.getAbsolutePath());
                    } else {
                        List<File> files = fileChooser.showOpenMultipleDialog(parentWindow.get());
                        if (files == null)
                            return;

                        textField.setText(files.stream()
                            .map(File::getAbsolutePath)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse(""));
                    }
                }
                case DIRECTORY -> {
                    var folderBrowser = folderBrowser(defaultLocation.toFile(), "Select Directory");
                    File file = folderBrowser.showDialog(parentWindow.get());
                    if (file == null)
                        return;

                    textField.setText(file.getAbsolutePath());
                }
                case IMAGE -> {
                    var imageChooser = imageBrowser(defaultLocation.toFile(), "Select Image");
                    if (selectionMode == BrowseSelectionMode.SINGLE) {
                        File file = imageChooser.showOpenDialog(parentWindow.get());
                        if (file == null)
                            return;

                        textField.setText(file.getAbsolutePath());
                    } else {
                        List<File> files = imageChooser.showOpenMultipleDialog(parentWindow.get());
                        if (files == null)
                            return;

                        textField.setText(files.stream()
                            .map(File::getAbsolutePath)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse(""));
                    }
                }
            }
        });
    }

    /**
     * Creates a {@link DirectoryChooser} for selecting directories.
     *
     * @param defaultPath The initial directory to open.
     * @param title The title of the dialog.
     * @return A {@link DirectoryChooser} instance.
     */
    public static DirectoryChooser folderBrowser(File defaultPath, String title) {
        var directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle(title);
        directoryChooser.setInitialDirectory(defaultPath);

        return directoryChooser;
    }

    /**
     * Creates a {@link FileChooser} for selecting files.
     *
     * @param defaultPath The initial directory to open.
     * @param title The title of the dialog.
     * @param filter An optional file extension filter.
     * @return A {@link FileChooser} instance.
     */
    public static FileChooser fileBrowser(
        File defaultPath,
        String title,
        @Nullable FileChooser.ExtensionFilter filter
    ) {
        var fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.setInitialDirectory(defaultPath);
        if (filter != null) {
            fileChooser.setSelectedExtensionFilter(filter);
        }

        return fileChooser;
    }

    /**
     * Creates a {@link FileChooser} for selecting image files.
     *
     * @param defaultPath The initial directory to open.
     * @param title The title of the dialog.
     * @return A {@link FileChooser} instance configured for image files.
     */
    public static FileChooser imageBrowser(File defaultPath, String title) {
        var filter = new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif");

        var fileChooser = new FileChooser();
        fileChooser.setTitle(title);
        fileChooser.setInitialDirectory(defaultPath);
        fileChooser.setSelectedExtensionFilter(filter);

        return fileChooser;
    }

    /**
     * Returns the owner-window property used for browser dialogs.
     *
     * @return The parent window property for this button.
     */
    public ObjectProperty<Window> parentWindowProperty() {
        return parentWindow;
    }

    /**
     * Returns the destination field property; multiple selected paths are joined with a comma and space.
     *
     * @return The text field property that will be updated with the selected path.
     */
    public ObjectProperty<TextField> textFieldProperty() {
        return textField;
    }

    /**
     * Returns the property choosing the file, directory, or image dialog; defaults to file browsing.
     *
     * @return The browse type property that determines whether to browse files, directories, or images.
     */
    public ObjectProperty<BrowseType> browseTypeProperty() {
        return browseType;
    }

    /**
     * Returns the selection mode property; directory dialogs always select one directory.
     *
     * @return The selection mode property that determines whether to allow single or multiple selections.
     */
    public ObjectProperty<BrowseSelectionMode> selectionModeProperty() {
        return selectionMode;
    }

    /**
     * Returns the initial-directory property; a null value falls back to the user's home directory.
     *
     * @return The default location property that specifies the initial directory for the browser dialog.
     */
    public ObjectProperty<Path> defaultLocationProperty() {
        return defaultLocation;
    }

    /**
     * Enum representing the type of browsing operation.
     */
    public enum BrowseType {
        /** Browse for files without an image-specific filter. */
        FILE,
        /** Browse for a single directory. */
        DIRECTORY,
        /** Browse using the configured PNG, JPEG, and GIF image filter. */
        IMAGE
    }

    /**
     * Enum representing the selection mode for the browse operation.
     * SINGLE allows selecting one item, MULTIPLE allows selecting multiple items.
     */
    public enum BrowseSelectionMode {
        /** Select one file or directory. */
        SINGLE,
        /** Select multiple files or images; directory browsing still selects one directory. */
        MULTIPLE
    }
}
