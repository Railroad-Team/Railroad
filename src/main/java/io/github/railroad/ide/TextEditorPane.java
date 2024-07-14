package io.github.railroad.ide;

import io.github.railroad.ui.defaults.RRBorderPane;
import javafx.scene.control.TextArea;
import javafx.scene.text.Font;

public class TextEditorPane extends RRBorderPane {
    public TextEditorPane() {
        var textArea = new TextArea();
        textArea.setWrapText(true);
        textArea.setFont(Font.font("monospace", 16));
        textArea.setPromptText("Start typing here...");

        setCenter(textArea);
    }
}