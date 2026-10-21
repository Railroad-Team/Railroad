package dev.railroadide.railroad.ide.projectexplorer.dialog;

import dev.railroadide.railroad.window.WindowBuilder;
import javafx.beans.property.BooleanProperty;

/**
 * Prompts for confirmation before replacing an existing clipboard destination.
 */
public class CopyModalDialog {
    /**
     * Shows a replacement confirmation and records the decision in the supplied property.
     *
     * @param replaceProperty property set to the user's replacement decision
     */
    public static void open(BooleanProperty replaceProperty) {
        WindowBuilder.createDialog(
            "railroad.dialog.copy.title",
            "railroad.dialog.copy.title",
            "railroad.dialog.copy.message",
            () -> replaceProperty.set(true),
            () -> replaceProperty.set(false));
    }
}
