package dev.railroadide.railroad.ide.projectexplorer.dialog;

import dev.railroadide.core.ui.RRGridPane;
import dev.railroadide.core.ui.localized.LocalizedButton;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.plugin.defaults.DefaultDocument;
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroadpluginapi.events.FileEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
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
        stage.setTitle("railroad.dialog.delete.title");

        var root = new RRGridPane();
        root.setPadding(new Insets(30));
        root.setHgap(5);
        root.setVgap(10);

        var label = new LocalizedLabel("railroad.dialog.delete.message");
        var okButton = new LocalizedButton("railroad.generic.ok");
        okButton.setOnAction(event -> {
            try {
                if (Files.isDirectory(path)) {
                    FileUtils.deleteFolder(path);
                } else {
                    Files.deleteIfExists(path);
                }

                stage.hide();

                Railroad.EVENT_BUS.publish(new FileEvent(new DefaultDocument(path.getFileName().toString(), path), FileEvent.EventType.DELETED));
            } catch (IOException exception) {
                Railroad.LOGGER.error("Failed to delete file or directory: {}", path, exception);
            }
        });

        var cancelButton = new LocalizedButton("railroad.generic.cancel");
        cancelButton.setOnAction(event -> stage.hide());

        root.add(label, 0, 0, 2, 1);
        root.addRow(1, okButton, cancelButton);
        stage.setScene(new Scene(root));
        stage.show();
    }
}
