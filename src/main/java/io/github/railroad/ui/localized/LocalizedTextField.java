package io.github.railroad.ui.localized;

import io.github.railroad.utility.localization.L18n;

import javafx.scene.control.TextField;

public class LocalizedTextField extends TextField {
    private String currentKey;
    public LocalizedTextField(final String key) {
        super();
        setKey(key);
        setPromptText(L18n.localize(key));
    }

    public void setKey(final String key) {
        currentKey = key;
        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setPromptText(L18n.localize(key)));
        setPromptText(L18n.localize(currentKey));
    }

    public String getKey() {
        return currentKey;
    }
}
