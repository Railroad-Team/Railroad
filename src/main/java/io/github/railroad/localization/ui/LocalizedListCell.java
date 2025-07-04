package io.github.railroad.localization.ui;

import io.github.railroad.localization.L18n;
import javafx.scene.control.ListCell;

import java.util.function.Function;

/**
 * An extension of the JavaFX ListCell that allows for the ListCell's text to be localised.
 * @param <T> The type of the ListCell items.
 */
public class LocalizedListCell<T> extends ListCell<T> {
    /**
     * Creates a LocalizedListCell with the given key function.
     * A listener is added to the List Cell to update the text when the item changes.
     * A listener is then added for when the language changes to allow for the text to be updated.
     * @param keyFunction The function that converts the object to a key to be localized.
     */
    public LocalizedListCell(Function<T, String> keyFunction) {
        itemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                setText(null);
            } else {
                setText(L18n.localize(keyFunction.apply(newValue)));
            }
        });

        L18n.currentLanguageProperty().addListener((observable, oldValue, newValue) -> {
            if (getItem() == null) {
                setText(null);
            } else {
                setText(L18n.localize(keyFunction.apply(getItem())));
            }
        });
    }
}