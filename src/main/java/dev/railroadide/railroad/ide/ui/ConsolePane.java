package dev.railroadide.railroad.ide.ui;

import javafx.scene.control.TextArea;

/**
 * Displays console output in a non-editable text area.
 */
public class ConsolePane extends TextArea {
    /**
     * Creates an empty, non-editable console.
     */
    public ConsolePane() {
        setEditable(false);
    }
}
