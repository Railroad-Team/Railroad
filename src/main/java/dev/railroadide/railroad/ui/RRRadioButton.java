package dev.railroadide.railroad.ui;

import dev.railroadide.railroad.ui.animation.UIAnimations;
import dev.railroadide.railroad.ui.localized.LocalizedTextProperty;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.RadioButton;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Railroad radio button with localized text, optional graphics, press animations, and a loading indicator.
 */
public class RRRadioButton extends RadioButton {
    /**
     * CSS classes installed when this control is initialized.
     */
    public static final String[] DEFAULT_STYLE_CLASSES = {"rr-radio-button", "radio-button"};

    private FontIcon icon;

    private Node originalGraphic;
    private FontIcon loadingSpinner;
    private RotateTransition loadingSpinnerAnimation;

    private final BooleanProperty isLoading = new SimpleBooleanProperty(this, "isLoading", false);

    /**
     * Reports whether the loading presentation is active.
     *
     * @return true while the control displays its loading indicator
     */
    public boolean getIsLoading() {
        return isLoading.get();
    }

    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    /**
     * Creates a radio button with empty text and default styling.
     */
    public RRRadioButton() {
        this("");
    }

    /**
     * Creates a radio button with an icon.
     *
     * @param localizationKey the translation key for the label
     * @param icon the icon to display, or null for no icon
     * @param args formatting arguments for the translation
     */
    public RRRadioButton(String localizationKey, Ikon icon, Object... args) {
        super();

        initialize(localizationKey, args);
        setIcon(icon);
    }

    /**
     * Creates a radio button with a custom graphic.
     *
     * @param localizationKey the translation key for the label
     * @param graphic the graphic to display, or null for none
     * @param args formatting arguments for the translation
     */
    public RRRadioButton(String localizationKey, Node graphic, Object... args) {
        super();

        initialize(localizationKey, args);
        setGraphic(graphic);
    }

    /**
     * Creates a radio button with localized text.
     *
     * @param localizationKey the translation key for the label
     * @param args formatting arguments for the translation
     */
    public RRRadioButton(String localizationKey, Object... args) {
        super();

        initialize(localizationKey, args);
    }

    /**
     * Installs default styling, localization bindings, loading feedback, and press animations.
     *
     * @param localizationKey the initial label translation key
     * @param args formatting arguments for the translation
     */
    protected void initialize(String localizationKey, Object... args) {
        getStyleClass().setAll(RRRadioButton.DEFAULT_STYLE_CLASSES);

        textProperty().bindBidirectional(localizedText);
        localizedText.setTranslation(localizationKey, args);

        loadingSpinner = new FontIcon(FontAwesomeSolid.SYNC_ALT);
        loadingSpinner.setIconSize(16);
        loadingSpinner.getStyleClass().add("loading-spinner");
        loadingSpinnerAnimation = UIAnimations.spinner(loadingSpinner);

        setOnMousePressed(_ -> {
            if (!getIsLoading()) {
                var scale = new ScaleTransition(Duration.millis(100), this);
                scale.setToX(0.95);
                scale.setToY(0.95);
                scale.play();
            }
        });

        setOnMouseReleased(_ -> {
            if (!getIsLoading()) {
                var scale = new ScaleTransition(Duration.millis(100), this);
                scale.setToX(1.0);
                scale.setToY(1.0);
                scale.play();
            }
        });

        isLoading.addListener(_ -> {
            if (getIsLoading()) {
                onLoading();
            } else {
                onNotLoading();
            }
        });

        updateContent();
    }

    /**
     * Set the button text using a localization key with optional formatting arguments.
     * The text will automatically update when the application language changes.
     *
     * @param localizationKey the localization key for the text
     * @param args optional formatting arguments for the localized text
     */
    public void setLocalizedText(String localizationKey, Object... args) {
        localizedText.setTranslation(localizationKey, args);
    }

    /**
     * Sets the icon used by the control's normal presentation.
     *
     * @param iconCode the icon to display, or null to remove the configured icon
     */
    public void setIcon(Ikon iconCode) {
        if (icon != null && getGraphic() == icon) {
            setGraphic(null);
        }

        if (iconCode != null) {
            icon = new FontIcon(iconCode);
            icon.setIconSize(16);
            icon.getStyleClass().add("button-icon");
        } else {
            icon = null;
        }

        if (!getIsLoading()) {
            updateContent();
        }
    }

    /**
     * Displays a spinner and disables the control while loading; re-enables it when loading ends.
     *
     * @param loading true to show loading feedback, false to restore the normal presentation
     */
    public void setLoading(boolean loading) {
        isLoading.set(loading);
    }

    /**
     * Disables the control, replaces its graphic with a spinner, and temporarily replaces localized text.
     */
    protected void onLoading() {
        textProperty().unbindBidirectional(localizedText);
        originalGraphic = getGraphic();

        setDisable(true);
        getStyleClass().add("loading");

        var loadingContent = new RRHBox();
        loadingContent.getStyleClass().add("rr-radio-button-content");
        loadingContent.setAlignment(Pos.CENTER);
        loadingContent.getChildren().addAll(loadingSpinner);

        if (localizedText.get() != null && !localizedText.get().isEmpty()) {
            setText("Loading...");
        } else {
            setText("");
        }

        setGraphic(loadingContent);
        loadingSpinnerAnimation.playFromStart();
    }

    /**
     * Stops the spinner, re-enables the control, and restores its localized text and graphic.
     */
    protected void onNotLoading() {
        loadingSpinnerAnimation.stop();
        loadingSpinner.setRotate(0);
        setDisable(false);
        getStyleClass().remove("loading");

        textProperty().bindBidirectional(localizedText);

        if (originalGraphic != null) {
            setGraphic(originalGraphic);
        } else {
            updateContent();
        }
    }

    private void updateContent() {
        if (getIsLoading())
            return; // Don't update content while loading

        if (icon != null) {
            var content = new RRHBox();
            content.getStyleClass().add("rr-radio-button-content");
            content.setAlignment(Pos.CENTER);
            content.getChildren().add(icon);

            if (getText() != null && !getText().isEmpty()) {
                setGraphic(content);
            } else {
                setGraphic(icon);
            }
        } else {
            setGraphic(null);
        }
    }

}
