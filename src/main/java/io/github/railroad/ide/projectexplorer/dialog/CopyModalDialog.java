package io.github.railroad.ide.projectexplorer.dialog;

import io.github.railroad.localization.ui.LocalizedButton;
import io.github.railroad.localization.ui.LocalizedLabel;
import io.github.railroad.ui.defaults.RRGridPane;
import javafx.beans.property.BooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public class CopyModalDialog {
    public static void open(Window owner, BooleanProperty replaceProperty) {
        var dialog = new Stage(StageStyle.UTILITY);
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("railroad.dialog.copy.title");

        var root = new RRGridPane();
        root.setPadding(new Insets(30));
        root.setHgap(5);
        root.setVgap(10);

        var label = new LocalizedLabel("railroad.dialog.copy.message");
        var okButton = new LocalizedButton("railroad.generic.ok");
        okButton.setOnAction(event -> {
            replaceProperty.set(true);
            dialog.hide();
        });

        var cancelButton = new LocalizedButton("railroad.generic.cancel");
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
