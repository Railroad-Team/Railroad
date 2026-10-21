package dev.railroadide.railroad.ui;

import dev.railroadide.railroad.ui.animation.UIAnimations;
import dev.railroadide.railroad.ui.localized.LocalizedTextProperty;
import dev.railroadide.railroad.ui.styling.ButtonSize;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A modern button component with enhanced styling, variants, and smooth animations.
 * Supports different sizes, styles, and icon integration.
 */
public class RRButton extends Button {
    /**
     * CSS classes installed when this control is initialized.
     */
    public static final String[] DEFAULT_STYLE_CLASSES = {"rr-button", "button"};

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

    private final BooleanProperty isSquare = new SimpleBooleanProperty(this, "isSquare", false);
    private final BooleanProperty isOutlined = new SimpleBooleanProperty(this, "isOutlined", false);
    private final BooleanProperty isFlat = new SimpleBooleanProperty(this, "isFlat", false);
    private final ObjectProperty<ButtonVariant> variant = new SimpleObjectProperty<>(this, "variant",
        ButtonVariant.PRIMARY);
    private final ObjectProperty<ButtonSize> size = new SimpleObjectProperty<>(this, "size", ButtonSize.MEDIUM);

    /**
     * Creates a button with empty text and default styling.
     */
    public RRButton() {
        this("");
    }

    /**
     * Creates a button with an icon.
     *
     * @param localizationKey the translation key for the label
     * @param icon the icon to display, or null for no icon
     * @param args formatting arguments for the translation
     */
    public RRButton(String localizationKey, Ikon icon, Object... args) {
        super();

        initialize(localizationKey, args);
        setIcon(icon);
    }

    /**
     * Creates a button with a custom graphic.
     *
     * @param localizationKey the translation key for the label
     * @param graphic the graphic to display, or null for none
     * @param args formatting arguments for the translation
     */
    public RRButton(String localizationKey, Node graphic, Object... args) {
        super();

        initialize(localizationKey, args);
        setGraphic(graphic);
    }

    /**
     * Creates a button with localized text.
     *
     * @param localizationKey the translation key for the label
     * @param args formatting arguments for the translation
     */
    public RRButton(String localizationKey, Object... args) {
        super();

        initialize(localizationKey, args);
    }

    /**
     * Creates a button with the primary visual variant.
     *
     * @param text the translation key for the button label
     * @return a new primary button
     */
    public static RRButton primary(String text) {
        var button = new RRButton(text);
        button.setVariant(ButtonVariant.PRIMARY);
        return button;
    }

    /**
     * Creates a button with the secondary visual variant.
     *
     * @param text the translation key for the button label
     * @return a new secondary button
     */
    public static RRButton secondary(String text) {
        var button = new RRButton(text);
        button.setVariant(ButtonVariant.SECONDARY);
        return button;
    }

    /**
     * Creates a button with the ghost visual variant.
     *
     * @param text the translation key for the button label
     * @return a new ghost button
     */
    public static RRButton ghost(String text) {
        var button = new RRButton(text);
        button.setVariant(ButtonVariant.GHOST);
        return button;
    }

    /**
     * Creates a button with the danger visual variant.
     *
     * @param text the translation key for the button label
     * @return a new danger button
     */
    public static RRButton danger(String text) {
        var button = new RRButton(text);
        button.setVariant(ButtonVariant.DANGER);
        return button;
    }

    /**
     * Creates a button with the success visual variant.
     *
     * @param text the translation key for the button label
     * @return a new success button
     */
    public static RRButton success(String text) {
        var button = new RRButton(text);
        button.setVariant(ButtonVariant.SUCCESS);
        return button;
    }

    /**
     * Creates a button with the warning visual variant.
     *
     * @param text the translation key for the button label
     * @return a new warning button
     */
    public static RRButton warning(String text) {
        var button = new RRButton(text);
        button.setVariant(ButtonVariant.WARNING);
        return button;
    }

    /**
     * Installs default styling, localization bindings, loading feedback, and press animations.
     *
     * @param localizationKey the initial label translation key
     * @param args formatting arguments for the translation
     */
    protected void initialize(String localizationKey, Object... args) {
        getStyleClass().setAll(RRButton.DEFAULT_STYLE_CLASSES);

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

        variant.addListener(_ -> updateStyle());
        size.addListener(_ -> updateStyle());
        isSquare.addListener(_ -> updateStyle());
        isOutlined.addListener(_ -> updateStyle());
        isFlat.addListener(_ -> updateStyle());

        updateStyle();
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
     * Sets the semantic color and emphasis variant.
     *
     * @param variant the visual variant to apply; must not be null
     */
    public void setVariant(ButtonVariant variant) {
        this.variant.set(variant);
    }

    /**
     * Sets the CSS size variant.
     *
     * @param size the button size to apply; must not be null
     */
    public void setButtonSize(ButtonSize size) {
        this.size.set(size);
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
     * Set loading state for the button.
     * <p>
     * When loading is true:
     * - The button becomes disabled and shows a spinning icon
     * - The text changes to "Loading..." if there was original text
     * - The button gets a "loading" CSS class for styling
     * - Click animations are disabled during loading
     * <p>
     * When loading is false:
     * - The button is re-enabled and shows the original content
     * - Original text and icon are restored
     * - The "loading" CSS class is removed
     * <p>
     * Example usage:
     *
     * <pre>
     * RRButton button = RRButton.primary("Save");
     * button.setOnAction(e -> {
     *     button.setLoading(true);
     *     // Perform async operation
     *     CompletableFuture.runAsync(() -> {
     *         // Do work...
     *         Platform.runLater(() -> button.setLoading(false));
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
        loadingContent.getStyleClass().add("rr-button-content");
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

    /**
     * Adds or removes rounded-corner styling.
     *
     * @param rounded true to enable rounded corners
     */
    public void setRounded(boolean rounded) {
        if (rounded) {
            getStyleClass().add("rounded");
        } else {
            getStyleClass().remove("rounded");
        }
    }

    /**
     * Adds or removes square button styling.
     *
     * @param square true to request a square shape
     */
    public void setSquare(boolean square) {
        isSquare.set(square);
    }

    /**
     * Adds or removes outlined button styling.
     *
     * @param outlined true to enable an outline
     */
    public void setOutlined(boolean outlined) {
        isOutlined.set(outlined);
    }

    /**
     * Adds or removes flat button styling.
     *
     * @param flat true to enable the flat variant
     */
    public void setFlat(boolean flat) {
        isFlat.set(flat);
    }

    private void updateContent() {
        if (getIsLoading())
            return; // Don't update content while loading

        if (icon != null) {
            var content = new RRHBox();
            content.getStyleClass().add("rr-button-content");
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

    private void updateStyle() {
        ObservableList<String> styleClass = getStyleClass();

        styleClass.removeAll("square", "outlined", "flat");
        styleClass.removeAll("primary", "secondary", "ghost", "danger", "success", "warning");
        styleClass.removeAll("small", "medium", "large");

        if (isSquare.get()) {
            styleClass.add("square");
        }

        if (isOutlined.get()) {
            styleClass.add("outlined");
        }

        if (isFlat.get()) {
            styleClass.add("flat");
        }

        switch (variant.get()) {
            case PRIMARY -> styleClass.add("primary");
            case SECONDARY -> styleClass.add("secondary");
            case GHOST -> styleClass.add("ghost");
            case DANGER -> styleClass.add("danger");
            case SUCCESS -> styleClass.add("success");
            case WARNING -> styleClass.add("warning");
        }

        switch (size.get()) {
            case SMALL -> styleClass.add("small");
            case MEDIUM -> styleClass.add("medium");
            case LARGE -> styleClass.add("large");
        }
    }
}
