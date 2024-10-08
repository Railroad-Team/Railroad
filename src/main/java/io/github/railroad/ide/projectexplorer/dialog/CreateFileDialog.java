package io.github.railroad.ide.projectexplorer.dialog;

import io.github.railroad.ide.projectexplorer.FileCreateType;
import io.github.railroad.ui.defaults.RRGridPane;
import io.github.railroad.ui.defaults.RRListView;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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

public class CreateFileDialog {
    public static void open(Window owner, Path path, FileCreateType type) {
        var dialog = new Stage(StageStyle.UTILITY);
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle(switch (type) { // TODO: Localize
            case FILE -> "Create File";
            case FOLDER -> "Create Folder";
            case JAVA_CLASS -> "Create Java Class";
            case JSON -> "Create JSON File";
            case TXT -> "Create Text File";
        });

        var root = new RRGridPane();
        root.setPadding(new Insets(30));
        root.setHgap(5);
        root.setVgap(10);

        var title = new Label(switch (type) { // TODO: Localize
            case FILE -> "Create File";
            case FOLDER -> "Create Folder";
            case JAVA_CLASS -> "Create Java Class";
            case JSON -> "Create JSON File";
            case TXT -> "Create Text File";
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

        var okButton = new Button("Ok"); // TODO: Localize
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
                    exception.printStackTrace(); // TODO: Handle exception
                }
            } else if (type == FileCreateType.FOLDER) {
                try {
                    Files.createDirectories(path.resolve(textField.getText()));
                } catch (IOException exception) {
                    exception.printStackTrace(); // TODO: Handle exception
                }
            } else {
                try {
                    Files.createFile(path.resolve(textField.getText()));
                } catch (IOException exception) {
                    exception.printStackTrace(); // TODO: Handle exception
                }
            }
        });

        var cancelButton = new Button("Cancel"); // TODO: Localize
        cancelButton.setOnAction(event -> dialog.hide());

        root.add(title, 0, 0, 2, 1);
        root.add(textField, 0, 1, 2, 1);
        if(!listView.getItems().isEmpty()) {
            root.add(listView, 0, 2, 2, 1);
            root.addRow(3, okButton, cancelButton);
        } else {
            root.addRow(2, okButton, cancelButton);
        }

        dialog.setScene(new Scene(root));
        dialog.show();
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
}
