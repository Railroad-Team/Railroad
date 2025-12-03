package dev.railroadide.core.form.ui;

import dev.railroadide.core.form.HasSetValue;
import dev.railroadide.core.ui.localized.LocalizedComboBox;
import dev.railroadide.core.utility.FromStringFunction;
import dev.railroadide.core.utility.ToStringFunction;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

/**
 * A form combo box component that extends InformativeLabeledHBox to provide
 * a labeled combo box with validation, localization, and styling support.
 *
 * @param <T> the type of items in the combo box
 */
@Getter
public class FormComboBox<T> extends InformativeLabeledHBox<ComboBox<T>> implements HasSetValue {
    /**
     * Constructs a new FormComboBox with the specified configuration.
     *
     * @param labelKey        the localization key for the label text
     * @param required        whether the combo box is required
     * @param editable        whether the combo box is editable
     * @param translate       whether to use localization for the items
     * @param keyFunction     the function to convert items to strings for display
     * @param valueOfFunction the function to convert strings back to items
     */
    public FormComboBox(String labelKey, boolean required, boolean editable, boolean translate, @Nullable Function<T, String> keyFunction) {
        super(labelKey, required, Map.of("editable", editable, "translate", translate, "keyFunction", keyFunction));
    }

    /**
     * Creates the primary combo box component with the specified parameters.
     *
     * @param params a map containing the parameters for the combo box
     * @return a new ComboBox instance with the specified configuration
     */
    @SuppressWarnings("unchecked")
    @Override
    public ComboBox<T> createPrimaryComponent(Map<String, Object> params) {
        boolean editable = (boolean) params.get("editable");
        boolean translate = (boolean) params.get("translate");
        Function<T, String> keyFunction = (Function<T, String>) params.get("keyFunction");

        ComboBox<T> comboBox = translate ? new LocalizedComboBox<>(keyFunction) : new ComboBox<>();
        comboBox.setEditable(editable);
        comboBox.getStyleClass().add("rr-combo-box");
        return comboBox;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void setValue(Object value) {
        Platform.runLater(() -> getPrimaryComponent().setValue((T) value));
    }
}
