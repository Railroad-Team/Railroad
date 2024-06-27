package io.github.railroad.ui.localized;

import io.github.railroad.utility.localization.L18n;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public class LocalizedLabel extends Label {
    private String key;
    private final ObservableList<Object> args = FXCollections.observableArrayList();

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