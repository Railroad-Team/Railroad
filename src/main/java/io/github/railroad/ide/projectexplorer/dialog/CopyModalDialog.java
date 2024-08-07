package io.github.railroad.ide.projectexplorer.dialog;

import io.github.railroad.ui.defaults.RRGridPane;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public class CopyModalDialog {
    public static void open(Window owner, BooleanProperty replaceProperty) {
        var dialog = new Stage(StageStyle.UTILITY);
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);

        var root = new RRGridPane();
        root.setPadding(new Insets(30));
        root.setHgap(5);
        root.setVgap(10);

        var label = new Label("The item already exists in this location. Do you want to replace it?"); // TODO: Localize
        var okButton = new Button("Ok"); // TODO: Localize
        okButton.setOnAction(event -> {
            replaceProperty.set(true);
            dialog.hide();
        });

        var cancelButton = new Button("Cancel"); // TODO: Localize
        cancelButton.setOnAction(event -> {
            replaceProperty.set(false);
            dialog.hide();
        });

        root.add(label, 0, 0, 2, 1);
        root.addRow(1, okButton, cancelButton);
        dialog.setScene(new Scene(root));
        dialog.show();
    }
}
