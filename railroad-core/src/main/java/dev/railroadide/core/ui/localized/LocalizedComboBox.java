package dev.railroadide.core.ui.localized;

import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.utility.FromStringFunction;
import dev.railroadide.core.utility.ServiceLocator;
import dev.railroadide.core.utility.ToStringFunction;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.Locale;

/**
 * An extension of the JavaFX ComboBox that allows for the ComboBox's items to have localised labels.
 *
 * @param <T> The type of the ComboBox items.
 */
public class LocalizedComboBox<T> extends ComboBox<T> {

    public static final String[] DEFAULT_STYLE_CLASSES = { "combo-box-base", "rr-combo-box", "combo-box" };

    private LocalizedComboBox() {
        super();
        initialize();
    }

    /**
     * Creates a new LocalizedComboBox with the given key and valueOf functions.
     *
     * @param keyFunction     The function that converts the object to a key.
     * @param valueOfFunction The function that converts the key to the object.
     */
    public LocalizedComboBox(ToStringFunction<T> keyFunction, FromStringFunction<T> valueOfFunction) {
        super();
        setConverter(new StringConverter<>() {
            @Override
            public String toString(T object) {
                if (object == null)
                    return "NullObject";

                String key = keyFunction.toString(object);
                if (key == null)
                    return "NullKey";

                return ServiceLocator.getService(LocalizationService.class).get(key);
            }

            @Override
            public T fromString(String string) {
                return valueOfFunction.fromString(string.toUpperCase(Locale.ROOT));
            }
        });
        initialize();
    }

    /**
     * Creates a new LocalizedComboBox with the given list of localization keys
     * @param localizationKeys
     * @return
     */
    public static LocalizedComboBox<String> fromLocalizationKeys(final String... localizationKeys)
    {
        LocalizedComboBox<String> cb = new LocalizedComboBox<>();

        for (String localizationKey : localizationKeys)
            cb.getItems().add(ServiceLocator.getService(LocalizationService.class).get(localizationKey));
            
        return cb;
    }

    protected void initialize() {
        getStyleClass().setAll(LocalizedComboBox.DEFAULT_STYLE_CLASSES);
    }
}
