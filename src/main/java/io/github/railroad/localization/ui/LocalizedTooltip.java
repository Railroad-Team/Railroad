package io.github.railroad.localization.ui;

import io.github.railroad.localization.L18n;
import javafx.scene.control.Tooltip;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class LocalizedTooltip extends Tooltip {
    private final AtomicReference<String> argString = new AtomicReference<>("");
    private String currentKey;

    public LocalizedTooltip(String key, String... args) {
        super();
        Arrays.stream(args).toList().forEach(e -> argString.set(argString + e));

        setKey(key);
        setText(L18n.localize(key) + argString);
    }

    public void setArgs(String... args) {
        Arrays.stream(args).toList().forEach(e -> argString.set(argString + e));
    }

    public String getKey() {
        return currentKey;
    }

    public void setKey(String key) {
        currentKey = key;
        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(L18n.localize(key) + argString));
        setText(L18n.localize(currentKey));
    }
}