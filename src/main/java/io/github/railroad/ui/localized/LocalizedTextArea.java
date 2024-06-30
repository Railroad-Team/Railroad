package io.github.railroad.ui.localized;

import io.github.railroad.utility.localization.L18n;
import javafx.scene.control.TextArea;

public class LocalizedTextArea extends TextArea {
    private String currentKey;

    public LocalizedTextArea(final String key) {
        super();
        if(key != null) {
            setKey(key);
            setPromptText(L18n.localize(key));
        }
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
