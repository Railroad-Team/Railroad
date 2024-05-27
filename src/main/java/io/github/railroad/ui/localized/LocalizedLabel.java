package io.github.railroad.ui.localized;

import javafx.scene.control.Label;

import io.github.railroad.utility.localization.L18n;

public class LocalizedLabel extends Label {
    private String currentKey;

    public LocalizedLabel(String key) {
        super();
        setKey(key);
        setText(L18n.localize(key));
    }

    public void setKey(String key) {
        currentKey = key;
        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(L18n.localize(key)));
    }

    public String getKey() {
        return currentKey;
    }
}
