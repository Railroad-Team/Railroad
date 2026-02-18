package dev.railroadide.railroad.ui.domain;

import dev.railroadide.railroad.ui.styling.TextFieldSize;
import dev.railroadide.railroad.ui.styling.ValidationState;
import org.kordamp.ikonli.Ikon;

public interface TextFieldControl {
    /**
     * Set the size of the text field
     */
    void setTextFieldSize(TextFieldSize size);

    /**
     * Set the validation state of the field
     */
    void setValidationState(ValidationState state);

    /**
     * Set a prefix icon
     */
    void setPrefixIcon(Ikon iconCode);

    /**
     * Set a suffix icon
     */
    void setSuffixIcon(Ikon iconCode);

    /**
     * Set the text field as rounded
     */
    void setRounded(boolean rounded);

    /**
     * Set the text field as outlined
     */
    void setOutlined(boolean outlined);

    /**
     * Set the text field as disabled state
     */
    void setDisabledState(boolean disabled);

    /**
     * Set placeholder text, displayed when the text field is empty
     */
    void setPlaceholder(String placeholder);

    /**
     * Set the placeholder text using a localization key
     *
     * @param args optional arguments to format the translation
     */
    void setLocalizedPlaceholder(String localizationKey, Object... args);

    /**
     * Clear the text field with an animation
     */
    void clearWithAnimation();
}
