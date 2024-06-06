package io.github.railroad.ui.localized;

import io.github.railroad.utility.localization.L18n;
import javafx.scene.text.Text;

public class LocalizedText extends Text {
    private String currentKey;

    public LocalizedText(final String key) {
        super();
        setKey(key);
        setText(L18n.localize(key));
    }

    public void setKey(final String key) {
        currentKey = key;
        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(L18n.localize(key)));
        setText(L18n.localize(currentKey));
    }

    public String getKey() {
        return currentKey;
    }
}