package dev.railroadide.core.ui;

import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.utility.ServiceLocator;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A modern text field component with enhanced styling, icons, and validation states.
 * Supports different sizes, validation states, and icon integration.
 */
public class RRTextField extends TextField {
    private TextFieldSize size = TextFieldSize.MEDIUM;
    private ValidationState validationState = ValidationState.NONE;
    private FontIcon prefixIcon;
    private FontIcon suffixIcon;
    @Getter
    private HBox container;

    /**
     * Constructs a new text field with empty text and default styling.
     */
    public RRTextField() {
        this("");
    }

    /**
     * Constructs a new text field with localized placeholder text and a prefix icon.
     *
     * @param localizationKey the localization key for the placeholder text
     * @param prefixIcon the icon to display before the text field
     */
    public RRTextField(String localizationKey, Ikon prefixIcon) {
        this(localizationKey);
        setPrefixIcon(prefixIcon);
    }

    /**
     * Constructs a new text field with localized placeholder text.
     *
     * @param localizationKey the localization key for the placeholder text
     * @param args optional formatting arguments for the localized text
     */
    public RRTextField(String localizationKey, Object... args) {
        super();
        setLocalizedPlaceholder(localizationKey, args);
        initialize();
    }

    private void initialize() {
        getStyleClass().addAll("rr-text-field", "text-field");
        setPadding(new Insets(8, 12, 8, 12));

        container = new HBox();
        container.setAlignment(Pos.CENTER_LEFT);
        container.setSpacing(8);

        focusedProperty().addListener((obs, oldVal, newVal) -> {
            var fade = new FadeTransition(Duration.millis(200), this);
            fade.setFromValue(newVal ? 0.8 : 1.0);
            fade.setToValue(newVal ? 1.0 : 0.8);
            fade.play();
        });

        updateStyle();
    }

    /**
     * Set the text field size
     */
    public void setTextFieldSize(TextFieldSize size) {
        this.size = size;
        updateStyle();
    }

    /**
     * Set the validation state
     */
    public void setValidationState(ValidationState state) {
        this.validationState = state;
        updateStyle();
    }

    /**
     * Set a prefix icon
     */
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
     * Set a suffix icon
     */
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
     * Set the text field as rounded
     */
    public void setRounded(boolean rounded) {
        if (rounded) {
            getStyleClass().add("rounded");
        } else {
            getStyleClass().remove("rounded");
        }
    }

    /**
     * Set the text field as outlined
     */
    public void setOutlined(boolean outlined) {
        if (outlined) {
            getStyleClass().add("outlined");
        } else {
            getStyleClass().remove("outlined");
        }
    }

    /**
     * Set the text field as disabled state
     */
    public void setDisabledState(boolean disabled) {
        setDisable(disabled);
        if (disabled) {
            getStyleClass().add("disabled");
        } else {
            getStyleClass().remove("disabled");
        }
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
        }
    }

    /**
     * Set placeholder text with modern styling
     */
    public void setPlaceholder(String placeholder) {
        setPromptText(placeholder);
    }

    /**
     * Clear the text field with animation
     */
    public void clearWithAnimation() {
        FadeTransition fade = new FadeTransition(Duration.millis(200), this);
        fade.setFromValue(1.0);
        fade.setToValue(0.5);
        fade.setOnFinished(e -> {
            setText("");
            FadeTransition fadeBack = new FadeTransition(Duration.millis(200), this);
            fadeBack.setFromValue(0.5);
            fadeBack.setToValue(1.0);
            fadeBack.play();
        });

        fade.play();
    }

    public void setLocalizedPlaceholder(String localizationKey, Object... args) {
        setPromptText(ServiceLocator.getService(LocalizationService.class).get(localizationKey, args));
        if (localizationKey != null) {
            ServiceLocator.getService(LocalizationService.class).currentLanguageProperty().addListener((observable, oldValue, newValue) ->
                    setPromptText(ServiceLocator.getService(LocalizationService.class).get(localizationKey, args)));
        }
    }

    public enum TextFieldSize {
        SMALL, MEDIUM, LARGE
    }

    public enum ValidationState {
        NONE, SUCCESS, ERROR, WARNING
    }
}
