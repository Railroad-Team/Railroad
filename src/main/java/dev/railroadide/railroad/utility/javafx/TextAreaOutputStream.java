package dev.railroadide.railroad.utility.javafx;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.io.OutputStream;

/**
 * An OutputStream that writes to a JavaFX TextArea.
 */
public class TextAreaOutputStream extends OutputStream {
    private final TextArea textArea;

    /**
     * Constructs a new TextAreaOutputStream that writes to the specified TextArea.
     *
     * @param textArea the TextArea to write to
     */
    public TextAreaOutputStream(TextArea textArea) {
        this.textArea = textArea;
    }

    @Override
    public void write(int b) {
        Platform.runLater(() -> textArea.appendText(String.valueOf((char) b)));
    }
}
