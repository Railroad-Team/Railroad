package dev.railroadide.core.ui.domain;

import org.kordamp.ikonli.Ikon;

import dev.railroadide.core.ui.styling.TextFieldSize;
import dev.railroadide.core.ui.styling.ValidationState;

public interface TextFieldControl {

    /**
     * Set the size of the text field
     * @param size
     */
    void setTextFieldSize(TextFieldSize size);

    /**
     * Set the validation state of the field
     * @param state
     */
    void setValidationState(ValidationState state);

    /**
     * Set a prefix icon
     * @param iconCode
     */
    void setPrefixIcon(Ikon iconCode);

    /**
     * Set a suffix icon
     * @param iconCode
     */
    void setSuffixIcon(Ikon iconCode);

    /**
     * Set the text field as rounded
     * @param rounded
     */
    void setRounded(boolean rounded);

    /**
     * Set the text field as outlined
     * @param outlined
     */
    void setOutlined(boolean outlined);

    /**
     * Set the text field as disabled state
     * @param disabled
     */
    void setDisabledState(boolean disabled);

    /**
     * Set placeholder text, displayed when the text field is empty
     * @param placeholder
     */
    void setPlaceholder(String placeholder);

    /**
     * Set the placeholder text using a localization key
     * @param localizationKey
     * @param args optional arguments to format the translation
     */
    void setLocalizedPlaceholder(String localizationKey, Object... args);

    /**
     * Clear the text field with an animation
     */
    void clearWithAnimation();
}
