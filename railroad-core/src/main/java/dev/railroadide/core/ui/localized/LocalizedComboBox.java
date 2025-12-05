package dev.railroadide.core.ui.localized;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import java.util.function.Function;

/**
 * An extension of the JavaFX ComboBox that allows for the ComboBox's items to have localised labels.
 *
 * @param <T> The type of the ComboBox items.
 */
public class LocalizedComboBox<T> extends ComboBox<T> {

    public static final String[] DEFAULT_STYLE_CLASSES = { "combo-box-base", "rr-combo-box", "combo-box" };

    /**
     * Create a new ComboBox that can localize it's items with a given key function
     */
    public LocalizedComboBox() {
        super();
        initialize();
    }

    /**
     * Create a new ComboBox that can localize it's items with a given key function
     * 
     * @param keyFunction A function that for any value T returns a localization key
     */
    public LocalizedComboBox(Function<T, String> keyFunction) {
        super();
        initialize();
        setKeyFunction(keyFunction);
    }

    /**
     * Creates a new LocalizedComboBox from a list of localizationKeys
     * 
     * @param localizationKeys A list of localization keys
     * @return
     */
    public static LocalizedComboBox<String> fromLocalizationKeys(ObservableList<String> localizationKeys) {
        LocalizedComboBox<String> combo = new LocalizedComboBox<>();
        combo.setKeyFunction(Function.identity());
        combo.setItems(localizationKeys);

        return combo;
    }

    protected void initialize() {
        getStyleClass().setAll(LocalizedComboBox.DEFAULT_STYLE_CLASSES);
    }

    /**
     * Assign a new key function to the LocalizedComboBox
     * 
     * @param keyFunction A function that for any value T returns a localization key
     */
    public void setKeyFunction(Function<T, String> keyFunction) {
        setCellFactory(list -> new LocalizedListCell<T>(keyFunction));
        setButtonCell(new LocalizedListCell<T>(keyFunction));
    }

}
