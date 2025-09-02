package dev.railroadide.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.railroadide.core.utility.ComboBoxConverter;
import dev.railroadide.core.utility.FromStringFunction;
import dev.railroadide.core.utility.ToStringFunction;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.TextField;

/**
 * Default setting codecs for common data types used in the Railroad IDE.
 * These codecs provide a way to convert between Java objects and their
 * corresponding UI components, as well as JSON representations.
 */
public class DefaultSettingCodecs {
    /**
     * Default setting codec for boolean values, using a CheckBox as the UI component.
     */
    public static final SettingCodec<Boolean, CheckBox> BOOLEAN =
        SettingCodec.<Boolean, CheckBox>builder("generic.boolean")
            .nodeToValue(CheckBox::isSelected)
            .valueToNode((selected, checkBox) -> checkBox.setSelected(selected))
            .jsonDecoder(JsonElement::getAsBoolean)
            .jsonEncoder(JsonPrimitive::new)
            .createNode(selected -> {
                var checkBox = new CheckBox();
                checkBox.setSelected(selected);
                return checkBox;
            })
            .build();

    /**
     * Default setting codec for string values, using a TextField as the UI component.
     */
    public static final SettingCodec<String, TextField> STRING =
        SettingCodec.<String, TextField>builder("generic.string")
            .nodeToValue(TextField::getText)
            .valueToNode((text, textField) -> textField.setText(text))
            .jsonDecoder(JsonElement::getAsString)
            .jsonEncoder(JsonPrimitive::new)
            .createNode(text -> {
                var textField = new TextField();
                textField.setText(text);
                return textField;
            })
            .build();

    /**
     * Default setting codec for integer values, using a TextField as the UI component.
     */
    public static final SettingCodec<Integer, TextField> INTEGER =
        SettingCodec.<Integer, TextField>builder("generic.integer")
            .nodeToValue(textField -> {
                try {
                    return Integer.parseInt(textField.getText());
                } catch (NumberFormatException e) {
                    return 0; // Default value if parsing fails
                }
            })
            .valueToNode((value, textField) -> textField.setText(String.valueOf(value)))
            .jsonDecoder(JsonElement::getAsInt)
            .jsonEncoder(JsonPrimitive::new)
            .createNode(value -> {
                var textField = new TextField();
                textField.setText(String.valueOf(value));
                return textField;
            })
            .build();

    /**
     * Default setting codec for double values, using a TextField as the UI component.
     */
    public static final SettingCodec<Double, TextField> DOUBLE =
        SettingCodec.<Double, TextField>builder("generic.double")
            .nodeToValue(textField -> {
                try {
                    return Double.parseDouble(textField.getText());
                } catch (NumberFormatException e) {
                    return 0.0; // Default value if parsing fails
                }
            })
            .valueToNode((value, textField) -> textField.setText(String.valueOf(value)))
            .jsonDecoder(JsonElement::getAsDouble)
            .jsonEncoder(JsonPrimitive::new)
            .createNode(value -> {
                var textField = new TextField();
                textField.setText(String.valueOf(value));
                return textField;
            })
            .build();

    /**
     * Default setting codec for float values, using a TextField as the UI component.
     */
    public static final SettingCodec<Float, TextField> FLOAT =
        SettingCodec.<Float, TextField>builder("generic.float")
            .nodeToValue(textField -> {
                try {
                    return Float.parseFloat(textField.getText());
                } catch (NumberFormatException e) {
                    return 0.0f; // Default value if parsing fails
                }
            })
            .valueToNode((value, textField) -> textField.setText(String.valueOf(value)))
            .jsonDecoder(JsonElement::getAsFloat)
            .jsonEncoder(JsonPrimitive::new)
            .createNode(value -> {
                var textField = new TextField();
                textField.setText(String.valueOf(value));
                return textField;
            })
            .build();

    /**
     * Default setting codec for long values, using a TextField as the UI component.
     */
    public static final SettingCodec<Long, TextField> LONG =
        SettingCodec.<Long, TextField>builder("generic.long")
            .nodeToValue(textField -> {
                try {
                    return Long.parseLong(textField.getText());
                } catch (NumberFormatException e) {
                    return 0L; // Default value if parsing fails
                }
            })
            .valueToNode((value, textField) -> textField.setText(String.valueOf(value)))
            .jsonDecoder(JsonElement::getAsLong)
            .jsonEncoder(JsonPrimitive::new)
            .createNode(value -> {
                var textField = new TextField();
                textField.setText(String.valueOf(value));
                return textField;
            })
            .build();

    public static <E extends Enum<E>> SettingCodec<E, ComboBox<E>> ofEnum(String id, Class<E> enumClass) {
        return ofEnum(id, enumClass, Enum::name, name -> {
            try {
                return Enum.valueOf(enumClass, name);
            } catch (IllegalArgumentException ignored) {
                return enumClass.getEnumConstants()[0];
            }
        });
    }

    public static <E extends Enum<E>> SettingCodec<E, ComboBox<E>> ofEnum(String id, Class<E> enumClass, ToStringFunction<E> toStringFunction, FromStringFunction<E> fromStringFunction) {
        return ofEnum(id, enumClass, toStringFunction, fromStringFunction, new ComboBoxConverter<>(toStringFunction, fromStringFunction));
    }

    public static <E extends Enum<E>> SettingCodec<E, ComboBox<E>> ofEnum(String id, Class<E> enumClass, ToStringFunction<E> toStringFunction, FromStringFunction<E> fromStringFunction, ComboBoxConverter<E> comboBoxConverter) {
        return SettingCodec.<E, ComboBox<E>>builder(id)
            .nodeToValue(ComboBoxBase::getValue)
            .valueToNode((value, comboBox) -> comboBox.setValue(value))
            .jsonDecoder(json -> fromStringFunction.fromString(json.getAsString()))
            .jsonEncoder(value -> new JsonPrimitive(toStringFunction.toString(value)))
            .createNode(value -> {
                var comboBox = new ComboBox<E>();
                comboBox.setConverter(comboBoxConverter);
                comboBox.getItems().addAll(enumClass.getEnumConstants());
                comboBox.setValue(value);
                return comboBox;
            })
            .build();
    }
}
