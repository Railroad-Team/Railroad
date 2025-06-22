package io.github.railroad.localization.ui;

import io.github.railroad.localization.L18n;
import javafx.scene.control.Tooltip;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An extension of the JavaFX Tooltip that allows for the Tooltip's text to be localised.
 */
public class LocalizedTooltip extends Tooltip {
    /*TODO why do we have 2 different ways of handling args?
     * We have 1 here and 1 in LocalizedLabel
     * Might just be me, but they look the same apart from this one using AtomicReference rather than an observable list
     */
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