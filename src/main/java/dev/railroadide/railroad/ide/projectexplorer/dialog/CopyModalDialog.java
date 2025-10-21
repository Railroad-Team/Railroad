package dev.railroadide.railroad.ide.projectexplorer.dialog;

import dev.railroadide.railroad.window.WindowBuilder;
import javafx.beans.property.BooleanProperty;

public class CopyModalDialog {
    public static void open(BooleanProperty replaceProperty) {
        WindowBuilder.createDialog(
            "railroad.dialog.copy.title",
            "railroad.dialog.copy.title",
            "railroad.dialog.copy.message",
            () -> replaceProperty.set(true),
            () -> replaceProperty.set(false)
        );
    }
}
