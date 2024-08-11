package io.github.railroad.localization.ui;

import io.github.railroad.localization.L18n;
import javafx.scene.text.Text;

public class LocalizedText extends Text {
    private String currentKey;

    public LocalizedText(final String key) {
        super();
        setKey(key);
        setText(L18n.localize(key));
    }

    public String getKey() {
        return currentKey;
    }

    public void setKey(final String key) {
        currentKey = key;
        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(L18n.localize(key)));
        setText(L18n.localize(currentKey));
    }
}