package io.github.railroad.ui.localized;

import io.github.railroad.utility.localization.L18n;
import javafx.scene.control.Label;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

public class LocalizedLabel extends Label {
    private String currentKey;
    private final AtomicReference<String> argString = new AtomicReference<>("");

    public LocalizedLabel(@NotNull String key, @NotNull String... args) {
        super();
        setKey(key, args);
    }

    public void setKey(@NotNull String key, @NotNull String... args) {
        for(String arg : args) {
            argString.set(argString + arg);
        }

        currentKey = key;
        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(L18n.localize(key) + argString));
        setText(L18n.localize(currentKey) + argString);
    }

    public String getKey() {
        return currentKey;
    }
}