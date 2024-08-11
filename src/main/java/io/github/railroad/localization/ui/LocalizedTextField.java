package io.github.railroad.localization.ui;

import io.github.railroad.localization.L18n;
import javafx.scene.control.TextField;

public class LocalizedTextField extends TextField {
    private String currentKey;

    public LocalizedTextField(final String key) {
        super();
        if (key != null) {
            setKey(key);
            setPromptText(L18n.localize(key));
        }
    }

    public String getKey() {
        return currentKey;
    }

    public void setKey(final String key) {
        currentKey = key;
        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setPromptText(L18n.localize(key)));
        setPromptText(L18n.localize(currentKey));
    }
}
