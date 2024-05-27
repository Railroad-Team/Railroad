package io.github.railroad.ui.localized;

import javafx.scene.control.Button;

import static io.github.railroad.utility.localization.L18n.createStringBinding;

public class LocalizedButton extends Button {
    private String currentKey;

    public LocalizedButton(String key) {
        super();
        this.setKey(key);
    }

    public void setKey(String key) {
        currentKey = key;
        this.textProperty().unbind();
        this.textProperty().bind(createStringBinding(key));
    }

    public String getKey() {
        return currentKey;
    }
}
