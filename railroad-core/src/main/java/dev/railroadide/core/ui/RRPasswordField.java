package dev.railroadide.core.ui;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

import dev.railroadide.core.ui.domain.TextFieldControl;
import dev.railroadide.core.ui.localized.LocalizedTextProperty;
import dev.railroadide.core.ui.styling.TextFieldSize;
import dev.railroadide.core.ui.styling.ValidationState;
import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Duration;
import lombok.Getter;

public class RRPasswordField extends PasswordField implements TextFieldControl {

    public static final String[] DEFAULT_STYLE_CLASSES = { "rr-text-field", "rr-password-field", "text-field", "password-field" };

    private TextFieldSize size = TextFieldSize.MEDIUM;
    private ValidationState validationState = ValidationState.NONE;
    private FontIcon prefixIcon;
    private FontIcon suffixIcon;

    @Getter
    private HBox container;

    private final LocalizedTextProperty localizedPromptText = new LocalizedTextProperty(this, "localizedPromptText", null);

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
     * @param prefixIcon      the icon to display before the text field
     */
    public RRPasswordField(String localizationKey, Ikon prefixIcon) {
        this(localizationKey);
        setPrefixIcon(prefixIcon);
    }

    /**
     * Constructs a new password field with localized placeholder text.
     *
     * @param localizationKey the localization key for the placeholder text
     * @param args            optional formatting arguments for the localized text
     */
    public RRPasswordField(String localizationKey, Object... args) {
        super();
        initialize();

        setLocalizedPlaceholder(localizationKey, args);
    }

    protected void initialize() {
        getStyleClass().setAll(RRPasswordField.DEFAULT_STYLE_CLASSES);
        setPadding(new Insets(8, 12, 8, 12));

        promptTextProperty().bindBidirectional(localizedPromptText);

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

    @Override
    public void setTextFieldSize(TextFieldSize size) {
        this.size = size;
        updateStyle();
    }

    @Override
    public void setValidationState(ValidationState state) {
        this.validationState = state;
        updateStyle();
    }

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

    @Override
    public void setRounded(boolean rounded) {
        if (rounded) {
            getStyleClass().add("rounded");
        } else {
            getStyleClass().remove("rounded");
        }
    }

    @Override
    public void setOutlined(boolean outlined) {
        if (outlined) {
            getStyleClass().add("outlined");
        } else {
            getStyleClass().remove("outlined");
        }
    }

    @Override
    public void setDisabledState(boolean disabled) {
        setDisable(disabled);

        if (disabled) {
            getStyleClass().add("disabled");
        } else {
            getStyleClass().remove("disabled");
        }
    }

    @Override
    public void setPlaceholder(String placeholder) {
        setPromptText(placeholder);
    }

    @Override
    public void setLocalizedPlaceholder(String localizationKey, Object... args) {
        localizedPromptText.setTranslation(localizationKey, args);
    }

    @Override
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
            default -> {}
        }
    }

}
