package io.github.railroad.ui.defaults;

import javafx.scene.control.TextField;

public class RRTextField extends TextField {
    public RRTextField(String placeholder) {
        super();
        getStyleClass().addAll("Railroad", "Text", "Input", "TextField");
        this.setPromptText(placeholder);
    }

    public RRTextField()
    {
        this("");
    }
}
