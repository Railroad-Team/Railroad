package dev.railroadide.railroad.ui;

import dev.railroadide.railroad.ui.domain.TextFieldControl;
import dev.railroadide.railroad.ui.localized.LocalizedTextProperty;
import dev.railroadide.railroad.ui.styling.TextFieldSize;
import dev.railroadide.railroad.ui.styling.ValidationState;
import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Password input with Railroad styling, localized prompt text, validation feedback, and optional icons.
 */
public class RRPasswordField extends PasswordField implements TextFieldControl {
    /**
     * CSS classes installed when the field is initialized.
     */
    public static final String[] DEFAULT_STYLE_CLASSES = {"rr-text-field", "rr-password-field", "text-field",
        "password-field"};

    private TextFieldSize size = TextFieldSize.MEDIUM;
    private ValidationState validationState = ValidationState.NONE;
    private FontIcon prefixIcon;
    private FontIcon suffixIcon;

    /**
     * Container populated with the field and its optional icons when an icon is configured.
     *
     * @return the container used to lay out the field and icons
     */
    @Getter
    private HBox container;

    private final LocalizedTextProperty localizedPromptText = new LocalizedTextProperty(this, "localizedPromptText",
        null);

    /**
     * Constructs a new password field with empty text and default styling.
     */
    public RRPasswordField() {
        this("");
    }

    /**
     * Constructs a new password field with localized placeholder text and a prefix icon.
     *
     * @param localizationKey the localization key for the placeholder text
     * @param prefixIcon the icon to display before the text field
     */
    public RRPasswordField(String localizationKey, Ikon prefixIcon) {
        this(localizationKey);
        setPrefixIcon(prefixIcon);
    }

    /**
     * Constructs a new password field with localized placeholder text.
     *
     * @param localizationKey the localization key for the placeholder text
     * @param args optional formatting arguments for the localized text
     */
    public RRPasswordField(String localizationKey, Object... args) {
        super();
        initialize();

        setLocalizedPlaceholder(localizationKey, args);
    }

    /**
     * Installs styling, binds localized prompt text, prepares the icon container, and adds focus feedback.
     */
    protected void initialize() {
        getStyleClass().setAll(RRPasswordField.DEFAULT_STYLE_CLASSES);
        setCursor(Cursor.TEXT);

        promptTextProperty().bindBidirectional(localizedPromptText);

        container = new HBox();
        container.setAlignment(Pos.CENTER_LEFT);
        container.getStyleClass().add("rr-text-field-container");

        focusedProperty().addListener((obs, oldVal, newVal) -> {
            var fade = new FadeTransition(Duration.millis(200), this);
            fade.setFromValue(newVal ? 0.8 : 1.0);
            fade.setToValue(newVal ? 1.0 : 0.8);
            fade.play();
        });

        updateStyle();
    }

    /**
     * Sets the CSS size variant.
     *
     * @param size the field size to apply; must not be null
     */
    @Override
    public void setTextFieldSize(TextFieldSize size) {
        this.size = size;
        updateStyle();
    }

    /**
     * Sets the visual validation state without validating the text.
     *
     * @param state the validation feedback to display; must not be null
     */
    @Override
    public void setValidationState(ValidationState state) {
        this.validationState = state;
        updateStyle();
    }

    /**
     * Sets the icon before the field and rebuilds the icon container.
     *
     * @param iconCode the prefix icon, or null to remove it
     */
    @Override
    public void setPrefixIcon(Ikon iconCode) {
        if (iconCode != null) {
            prefixIcon = new FontIcon(iconCode);
            prefixIcon.setIconSize(16);
            prefixIcon.getStyleClass().add("prefix-icon");
        } else {
            prefixIcon = null;
        }

        updateIcons();
    }

    /**
     * Sets the icon after the field and rebuilds the icon container.
     *
     * @param iconCode the suffix icon, or null to remove it
     */
    @Override
    public void setSuffixIcon(Ikon iconCode) {
        if (iconCode != null) {
            suffixIcon = new FontIcon(iconCode);
            suffixIcon.setIconSize(16);
            suffixIcon.getStyleClass().add("suffix-icon");
        } else {
            suffixIcon = null;
        }

        updateIcons();
    }

    /**
     * Adds or removes rounded-corner styling.
     *
     * @param rounded true to enable rounded corners
     */
    @Override
    public void setRounded(boolean rounded) {
        if (rounded) {
            getStyleClass().add("rounded");
        } else {
            getStyleClass().remove("rounded");
        }
    }

    /**
     * Adds or removes outlined field styling.
     *
     * @param outlined true to enable an outline
     */
    @Override
    public void setOutlined(boolean outlined) {
        if (outlined) {
            getStyleClass().add("outlined");
        } else {
            getStyleClass().remove("outlined");
        }
    }

    /**
     * Updates both JavaFX disable state and the disabled CSS class.
     *
     * @param disabled true to disable the field
     */
    @Override
    public void setDisabledState(boolean disabled) {
        setDisable(disabled);

        if (disabled) {
            getStyleClass().add("disabled");
        } else {
            getStyleClass().remove("disabled");
        }
    }

    /**
     * Sets the literal prompt displayed when the field is empty.
     *
     * @param placeholder the prompt text
     */
    @Override
    public void setPlaceholder(String placeholder) {
        setPromptText(placeholder);
    }

    /**
     * Sets prompt text that follows the application's selected language.
     *
     * @param localizationKey the translation key for the prompt
     * @param args formatting arguments for the translation
     */
    @Override
    public void setLocalizedPlaceholder(String localizationKey, Object... args) {
        localizedPromptText.setTranslation(localizationKey, args);
    }

    /**
     * Fades the field out, clears its text when that fade finishes, then restores full opacity.
     */
    @Override
    public void clearWithAnimation() {
        var fade = new FadeTransition(Duration.millis(200), this);
        fade.setFromValue(1.0);
        fade.setToValue(0.5);
        fade.setOnFinished(e -> {
            setText("");
            var fadeBack = new FadeTransition(Duration.millis(200), this);
            fadeBack.setFromValue(0.5);
            fadeBack.setToValue(1.0);
            fadeBack.play();
        });

        fade.play();
    }

    private void updateIcons() {
        container.getChildren().clear();

        if (prefixIcon != null) {
            container.getChildren().add(prefixIcon);
        }

        container.getChildren().add(this);
        HBox.setHgrow(this, Priority.ALWAYS);

        if (suffixIcon != null) {
            container.getChildren().add(suffixIcon);
        }
    }

    private void updateStyle() {
        getStyleClass().removeAll("small", "medium", "large");
        getStyleClass().removeAll("success", "error", "warning");

        switch (size) {
            case SMALL -> getStyleClass().add("small");
            case MEDIUM -> getStyleClass().add("medium");
            case LARGE -> getStyleClass().add("large");
        }

        switch (validationState) {
            case SUCCESS -> getStyleClass().add("success");
            case ERROR -> getStyleClass().add("error");
            case WARNING -> getStyleClass().add("warning");
            default -> {
            }
        }
    }

}
