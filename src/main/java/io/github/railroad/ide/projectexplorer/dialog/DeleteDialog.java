package io.github.railroad.ide.projectexplorer.dialog;

import io.github.railroad.ui.defaults.RRGridPane;
import io.github.railroad.utility.FileHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DeleteDialog {
    public static void open(Window owner, Path path) {
        var stage = new Stage(StageStyle.UTILITY);
        stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Delete file?"); // TODO: Localize

        var root = new RRGridPane();
        root.setPadding(new Insets(30));
        root.setHgap(5);
        root.setVgap(10);

        var label = new Label("Are you sure you want to delete the file?"); // TODO: Localize
        var okButton = new Button("Ok"); // TODO: Localize
        okButton.setOnAction(event -> {
            try {
                if(Files.isDirectory(path)) {
                    FileHandler.deleteFolder(path);
                } else {
                    Files.deleteIfExists(path);
                }

                stage.hide();
            } catch (IOException exception) {
                exception.printStackTrace(); // TODO: Handle exception
            }
        });

        var cancelButton = new Button("Cancel"); // TODO: Localize
        cancelButton.setOnAction(event -> stage.hide());

        root.add(label, 0, 0, 2, 1);
        root.addRow(1, okButton, cancelButton);
        stage.setScene(new Scene(root));
        stage.show();
    }
}
