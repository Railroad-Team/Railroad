package io.github.railroad.localization.ui;

import io.github.railroad.localization.L18n;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public class LocalizedLabel extends Label {
    private final ObservableList<Object> args = FXCollections.observableArrayList();
    private String key;

    public LocalizedLabel(@NotNull String key, @NotNull Object... args) {
        super();
        setKey(key, args);
    }

    public void setKey(@NotNull String key, @NotNull Object... args) {
        this.args.setAll(args);
        this.key = key;

        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                setText(L18n.localize(key).formatted(args)));
        setText(L18n.localize(this.key).formatted(args));
    }
}