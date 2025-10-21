package dev.railroadide.railroad.ide.projectexplorer.dialog;

import dev.railroadide.core.ui.RRGridPane;
import dev.railroadide.core.ui.RRListView;
import dev.railroadide.core.ui.localized.LocalizedButton;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.projectexplorer.FileCreateType;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// TODO: Refactor this to use WindowBuilder(?)
public class CreateFileDialog {
    public static void open(Window owner, Path path, FileCreateType type) {
        var dialog = new Stage(StageStyle.UTILITY);
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(switch (type) {
            case FILE -> "railroad.dialog.create_file.title.file";
            case FOLDER -> "railroad.dialog.create_file.title.folder";
            case JAVA_CLASS -> "railroad.dialog.create_file.title.java_class";
            case JSON -> "railroad.dialog.create_file.title.json";
            case TXT -> "railroad.dialog.create_file.title.txt";
        });

        var root = new RRGridPane();
        root.setPadding(new Insets(30));
        root.setHgap(5);
        root.setVgap(10);

        var title = new LocalizedLabel(switch (type) {
            case FILE -> "railroad.dialog.create_file.title.file";
            case FOLDER -> "railroad.dialog.create_file.title.folder";
            case JAVA_CLASS -> "railroad.dialog.create_file.title.java_class";
            case JSON -> "railroad.dialog.create_file.title.json";
            case TXT -> "railroad.dialog.create_file.title.txt";
        });

        var textField = new TextField();
        textField.setText(switch (type) {
            case FOLDER -> path.resolve("New Folder").toString();
            default -> "";
        });

        var listView = new RRListView<TypeSelection>();
        listView.setCellFactory(param -> new TypeSelectorCell());
        listView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                textField.setText(switch (type) {
                    case FOLDER -> path.resolve("New Folder").toString();
                    default -> "";
                });
            }
        });

        listView.getItems().addAll(switch (type) {
            case JAVA_CLASS ->
                    new TypeSelection[]{TypeSelection.JAVA_CLASS, TypeSelection.JAVA_INTERFACE, TypeSelection.JAVA_ENUM, TypeSelection.JAVA_ANNOTATION};
            case JSON -> new TypeSelection[]{TypeSelection.JSON};
            case TXT -> new TypeSelection[]{TypeSelection.TXT};
            default -> new TypeSelection[0];
        });

        listView.getSelectionModel().selectFirst();

        var okButton = new LocalizedButton("railroad.generic.ok");
        okButton.setOnAction(event -> {
            dialog.hide();

            TypeSelection selectedItem = listView.getSelectionModel().getSelectedItem();
            if (type != FileCreateType.FOLDER && selectedItem != null) {
                var template = selectedItem.getTemplate();
                var fileName = textField.getText();
                var extension = selectedItem.getExtension();
                var content = template
                        .replace("<package_loc>", path.toString().replace("\\", ".").replace("/", "."))
                        .replace("<class_name>", fileName);

                try {
                    Path file = path.resolve(fileName + "." + extension);
                    Files.createDirectories(file.getParent());
                    Files.writeString(file, content);
                } catch (IOException exception) {
                    Railroad.LOGGER.error("Failed to create file", exception);
                }
            } else if (type == FileCreateType.FOLDER) {
                try {
                    Files.createDirectories(path.resolve(textField.getText()));
                } catch (IOException exception) {
                    Railroad.LOGGER.error("Failed to create folder", exception);
                }
            } else {
                try {
                    Files.createFile(path.resolve(textField.getText()));
                } catch (IOException exception) {
                    Railroad.LOGGER.error("Failed to create file", exception);
                }
            }
        });

        var cancelButton = new LocalizedButton("railroad.generic.cancel");
        cancelButton.setOnAction(event -> dialog.hide());

        root.add(title, 0, 0, 2, 1);
        root.add(textField, 0, 1, 2, 1);
        if (!listView.getItems().isEmpty()) {
            root.add(listView, 0, 2, 2, 1);
            root.addRow(3, okButton, cancelButton);
        } else {
            root.addRow(2, okButton, cancelButton);
        }

        dialog.setScene(new Scene(root));
        dialog.showAndWait();
    }

    @Getter
    public enum TypeSelection {
        JAVA_CLASS("Java Class", "java", null, """
                package <package_loc>.<class_name>;

                public class <class_name> {
                    public <class_name>() {

                    }
                }
                """),
        JAVA_INTERFACE("Java Interface", "java", null, """
                package <package_loc>.<class_name>;

                public interface <class_name> {

                }
                """),
        JAVA_ENUM("Java Enum", "java", null, """
                package <package_loc>.<class_name>;

                public enum <class_name> {

                }
                """),
        JAVA_ANNOTATION("Java Annotation", "java", null, """
                package <package_loc>.<class_name>;

                public @interface <class_name> {

                }
                """),
        JSON("JSON File", "json", null, """
                {

                }
                """),
        TXT("Text File", "txt", null, "");


        private final String name;
        private final String extension;
        private final ImageView icon;
        private final String template;

        TypeSelection(String name, String extension, Image icon, String template) {
            this.name = name;
            this.extension = extension;
            this.icon = new ImageView(icon);
            this.template = template;
        }
    }

    public static class TypeSelectorCell extends ListCell<TypeSelection> {
        @Override
        protected void updateItem(TypeSelection item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else {
                setText(item.getName());
                setGraphic(item.getIcon());
            }
        }
    }
}
