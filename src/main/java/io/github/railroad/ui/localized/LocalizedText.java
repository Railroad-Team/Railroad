package io.github.railroad.ui.localized;

import io.github.railroad.utility.localization.L18n;
import javafx.scene.text.Text;

public class LocalizedText extends Text {
    private String currentKey;

    public LocalizedText(final String key) {
        super();
        this.setKey(key);
    }

    public void setKey(final String key) {
        currentKey = key;
        this.textProperty().unbind();
        this.textProperty().bind(L18n.createStringBinding(key));
    }

    public String getKey() {
        return currentKey;
    }
}
