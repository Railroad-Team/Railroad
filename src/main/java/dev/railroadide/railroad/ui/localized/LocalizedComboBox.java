package dev.railroadide.railroad.ui.localized;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;

import java.util.function.Function;

/**
 * An extension of the JavaFX ComboBox that allows for the ComboBox's items to have localised labels.
 *
 * @param <T> The type of the ComboBox items.
 */
public class LocalizedComboBox<T> extends ComboBox<T> {

    /** CSS style classes installed when a localized combo box is initialized. */
    public static final String[] DEFAULT_STYLE_CLASSES = {"combo-box-base", "rr-combo-box", "combo-box"};

    /**
     * Creates an empty styled combo box. Call {@link #setKeyFunction(Function)} to enable localized cells.
     */
    public LocalizedComboBox() {
        super();
        initialize();
    }

    /**
     * Creates an empty styled combo box whose popup and selected value use localized cells.
     *
     * @param keyFunction A function that for any value T returns a localization key
     */
    public LocalizedComboBox(Function<T, String> keyFunction) {
        super();
        initialize();
        setKeyFunction(keyFunction);
    }

    /**
     * Creates a combo box whose items are themselves localization keys.
     *
     * @param localizationKeys A list of localization keys
     * @return a localized combo box backed by the supplied observable list
     */
    public static LocalizedComboBox<String> fromLocalizationKeys(ObservableList<String> localizationKeys) {
        LocalizedComboBox<String> combo = new LocalizedComboBox<>();
        combo.setKeyFunction(Function.identity());
        combo.setItems(localizationKeys);

        return combo;
    }

    /** Replaces the control's style classes with {@link #DEFAULT_STYLE_CLASSES}. */
    protected void initialize() {
        getStyleClass().setAll(LocalizedComboBox.DEFAULT_STYLE_CLASSES);
    }

    /**
     * Replaces the popup cell factory and selected-value cell with cells using the supplied key function.
     *
     * @param keyFunction A function that for any value T returns a localization key
     */
    public void setKeyFunction(Function<T, String> keyFunction) {
        setCellFactory(list -> new LocalizedListCell<T>(keyFunction));
        setButtonCell(new LocalizedListCell<T>(keyFunction));
    }

}
