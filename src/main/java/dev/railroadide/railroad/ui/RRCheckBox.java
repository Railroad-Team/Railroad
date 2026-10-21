package dev.railroadide.railroad.ui;

import dev.railroadide.railroad.ui.animation.UIAnimations;
import dev.railroadide.railroad.ui.localized.LocalizedTextProperty;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Railroad checkbox with localized text, optional graphics, press animations, and a loading indicator.
 */
public class RRCheckBox extends CheckBox {

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
     * Creates a checkbox with empty text and default styling.
     */
    public RRCheckBox() {
        this("");
    }

    /**
     * Creates a checkbox with an icon.
     *
     * @param localizationKey the translation key for the label
     * @param icon the icon to display, or null for no icon
     * @param args formatting arguments for the translation
     */
    public RRCheckBox(String localizationKey, Ikon icon, Object... args) {
        super();

        initialize(localizationKey, args);
        setIcon(icon);
    }

    /**
     * Creates a checkbox with a custom graphic.
     *
     * @param localizationKey the translation key for the label
     * @param graphic the graphic to display, or null for none
     * @param args formatting arguments for the translation
     */
    public RRCheckBox(String localizationKey, Node graphic, Object... args) {
        super();

        initialize(localizationKey, args);
        setGraphic(graphic);
    }

    /**
     * Creates a checkbox with localized text.
     *
     * @param localizationKey the translation key for the label
     * @param args formatting arguments for the translation
     */
    public RRCheckBox(String localizationKey, Object... args) {
        super();

        initialize(localizationKey, args);
    }

    private void initialize(String localizationKey, Object... args) {
        getStyleClass().setAll("rr-check-box", "check-box");
        setAlignment(Pos.CENTER);

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
     * Set the checkbox text using a localization key with optional formatting arguments.
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
        } else {
            icon = null;
        }

        if (!getIsLoading()) {
            updateContent();
        }
    }

    /**
     * Set loading state for the checkbox.
     * <p>
     * When loading is true:
     * - The checkbox becomes disabled and shows a spinning icon
     * - The text changes to "Loading..." if there was original text
     * - The checkbox gets a "loading" CSS class for styling
     * - Click animations are disabled during loading
     * <p>
     * When loading is false:
     * - The checkbox is re-enabled and shows the original content
     * - Original text and icon are restored
     * - The "loading" CSS class is removed
     * <p>
     * Example usage:
     *
     * <pre>
     * RRCheckBox checkbox = new RRCheckBox();
     * checkbox.setOnAction(e -> {
     *     checkbox.setLoading(true);
     *     // Perform async operation
     *     CompletableFuture.runAsync(() -> {
     *         // Do work...
     *         Platform.runLater(() -> checkbox.setLoading(false));
     *     });
     * });
     * </pre>
     *
     * @param loading true to show loading state, false to restore normal state
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
        loadingContent.getStyleClass().add("rr-check-box-content");
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
            content.getStyleClass().add("rr-check-box-content");
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
