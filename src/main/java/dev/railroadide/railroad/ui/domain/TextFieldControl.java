package dev.railroadide.railroad.ui.domain;

import dev.railroadide.railroad.ui.styling.TextFieldSize;
import dev.railroadide.railroad.ui.styling.ValidationState;
import org.kordamp.ikonli.Ikon;

/**
 * Shared styling, icon, placeholder, and clearing operations for Railroad text input controls.
 */
public interface TextFieldControl {
    /**
     * Sets the size preset of the text field.
     *
     * @param size size preset to apply
     */
    void setTextFieldSize(TextFieldSize size);

    /**
     * Sets the visual validation state of the field.
     *
     * @param state validation styling to apply
     */
    void setValidationState(ValidationState state);

    /**
     * Sets the icon displayed before the input text.
     *
     * @param iconCode icon to display, or null to remove the prefix icon
     */
    void setPrefixIcon(Ikon iconCode);

    /**
     * Sets the icon displayed after the input text.
     *
     * @param iconCode icon to display, or null to remove the suffix icon
     */
    void setSuffixIcon(Ikon iconCode);

    /**
     * Enables or disables rounded styling.
     *
     * @param rounded whether rounded styling is enabled
     */
    void setRounded(boolean rounded);

    /**
     * Enables or disables outlined styling.
     *
     * @param outlined whether outlined styling is enabled
     */
    void setOutlined(boolean outlined);

    /**
     * Sets whether the field accepts user interaction.
     *
     * @param disabled whether the field is disabled
     */
    void setDisabledState(boolean disabled);

    /**
     * Sets placeholder text, displayed when the text field is empty.
     *
     * @param placeholder prompt text to display
     */
    void setPlaceholder(String placeholder);

    /**
     * Sets the placeholder text using a localization key.
     *
     * @param localizationKey translation key for the prompt
     * @param args optional arguments to format the translation
     */
    void setLocalizedPlaceholder(String localizationKey, Object... args);

    /**
     * Clears the input text using the control's clearing animation.
     */
    void clearWithAnimation();
}
