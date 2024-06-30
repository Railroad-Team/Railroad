package io.github.railroad.localization.ui;

import io.github.railroad.localization.L18n;
import javafx.scene.control.ListCell;

import java.util.function.Function;

public class LocalizedListCell<T> extends ListCell<T> {
    public LocalizedListCell(Function<T, String> keyFunction) {
        itemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                setText(L18n.localize(keyFunction.apply(newValue)));
            } else {
                setText(null);
            }
        });

        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) -> {
            if (getItem() != null) {
                setText(L18n.localize(keyFunction.apply(getItem())));
            } else {
                setText(null);
            }
        });
    }
}