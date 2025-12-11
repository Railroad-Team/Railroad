package dev.railroadide.core.ui;

import dev.railroadide.core.ui.localized.LocalizedTextProperty;
import dev.railroadide.core.ui.styling.ButtonSize;
import dev.railroadide.core.ui.styling.ButtonVariant;
import javafx.animation.ScaleTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
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

    public static final String[] DEFAULT_STYLE_CLASSES = { "rr-button", "button" };

    private FontIcon icon;

    private Node originalGraphic;
    private FontIcon loadingSpinner;

    private final BooleanProperty isLoading = new SimpleBooleanProperty(this, "isLoading", false);
    public boolean getIsLoading() {
        return isLoading.get();
    }
    
    private final LocalizedTextProperty localizedText = new LocalizedTextProperty(this, "localizedText", null);

    private final BooleanProperty isSquare = new SimpleBooleanProperty(this, "isSquare", false);
    private final BooleanProperty isOutlined = new SimpleBooleanProperty(this, "isOutlined", false);
    private final BooleanProperty isFlat = new SimpleBooleanProperty(this, "isFlat", false);
    private final ObjectProperty<ButtonVariant> variant = new SimpleObjectProperty<>(this, "variant", ButtonVariant.PRIMARY);
    private final ObjectProperty<ButtonSize> size = new SimpleObjectProperty<>(this, "size", ButtonSize.MEDIUM);

    public RRButton() {
        this("");
    }

    public RRButton(String localizationKey, Ikon icon, Object... args) {
        super();

        initialize(localizationKey, args);
        setIcon(icon);
    }

    public RRButton(String localizationKey, Node graphic, Object... args) {
        super();

        initialize(localizationKey, args);
        setGraphic(graphic);
    }

    public RRButton(String localizationKey, Object... args) {
        super();

        initialize(localizationKey, args);
    }

    /**
     * Create a primary button
     */
    public static RRButton primary(String text) {
        var button = new RRButton(text);
        button.setVariant(ButtonVariant.PRIMARY);
        return button;
    }

    /**
     * Create a secondary button
     */
    public static RRButton secondary(String text) {
        var button = new RRButton(text);
        button.setVariant(ButtonVariant.SECONDARY);
        return button;
    }

    /**
     * Create a ghost button
     */
    public static RRButton ghost(String text) {
        var button = new RRButton(text);
        button.setVariant(ButtonVariant.GHOST);
        return button;
    }

    /**
     * Create a danger button
     */
    public static RRButton danger(String text) {
        var button = new RRButton(text);
        button.setVariant(ButtonVariant.DANGER);
        return button;
    }

    /**
     * Create a success button
     */
    public static RRButton success(String text) {
        var button = new RRButton(text);
        button.setVariant(ButtonVariant.SUCCESS);
        return button;
    }

    /**
     * Create a warning button
     */
    public static RRButton warning(String text) {
        var button = new RRButton(text);
        button.setVariant(ButtonVariant.WARNING);
        return button;
    }

    protected void initialize(String localizationKey, Object... args) {
        getStyleClass().setAll(RRButton.DEFAULT_STYLE_CLASSES);
        
        setAlignment(Pos.CENTER);
        setPadding(new Insets(8, 16, 8, 16));

        textProperty().bindBidirectional(localizedText);
        localizedText.setTranslation(localizationKey, args);

        loadingSpinner = new FontIcon(FontAwesomeSolid.SYNC_ALT);
        loadingSpinner.setIconSize(16);
        loadingSpinner.getStyleClass().add("loading-spinner");

        setOnMousePressed($ -> {
            if (!getIsLoading()) {
                var scale = new ScaleTransition(Duration.millis(100), this);
                scale.setToX(0.95);
                scale.setToY(0.95);
                scale.play();
            }
        });

        setOnMouseReleased($ -> {
            if (!getIsLoading()) {
                var scale = new ScaleTransition(Duration.millis(100), this);
                scale.setToX(1.0);
                scale.setToY(1.0);
                scale.play();
            }
        });

        isLoading.addListener($ -> {
            if (getIsLoading()) {
                onLoading();
            } else {
                onNotLoading();
            }
        });

        variant.addListener($ -> updateStyle());
        size.addListener($ -> updateStyle());
        isSquare.addListener($ -> updateStyle());
        isOutlined.addListener($ -> updateStyle());
        isFlat.addListener($ -> updateStyle());

        updateStyle();
        updateContent();
    }

    /**
     * Set the button text using a localization key with optional formatting arguments.
     * The text will automatically update when the application language changes.
     *
     * @param localizationKey the localization key for the text
     * @param args            optional formatting arguments for the localized text
     */
    public void setLocalizedText(String localizationKey, Object... args) {
        localizedText.setTranslation(localizationKey, args);
    }

    /**
     * Set the button variant
     */
    public void setVariant(ButtonVariant variant) {
        this.variant.set(variant);
    }

    /**
     * Set the button size
     */
    public void setButtonSize(ButtonSize size) {
        this.size.set(size);
    }

    /**
     * Set an icon for the button
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
     * Called when the button has started loading
     */
    protected void onLoading() {
        textProperty().unbindBidirectional(localizedText);
        originalGraphic = getGraphic();

        setDisable(true);
        getStyleClass().add("loading");

        var loadingContent = new RRHBox(8);
        loadingContent.setAlignment(Pos.CENTER);
        loadingContent.getChildren().addAll(loadingSpinner);

        if (localizedText.get() != null && !localizedText.get().isEmpty()) {
            setText("Loading...");
        } else {
            setText("");
        }

        setGraphic(loadingContent);
    }

    /**
     * Called when the button has stopped loading
     */
    protected void onNotLoading() {
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
     * Set the button as rounded
     */
    public void setRounded(boolean rounded) {
        if (rounded) {
            getStyleClass().add("rounded");
        } else {
            getStyleClass().remove("rounded");
        }
    }

    /**
     * Force the button into a square shape.
     */
    public void setSquare(boolean square) {
        isSquare.set(square);
    }

    /**
     * Set the button as outlined
     */
    public void setOutlined(boolean outlined) {
        isOutlined.set(outlined);
    }

    /**
     * Set the button as flat
     */
    public void setFlat(boolean flat) {
        isFlat.set(flat);
    }

    private void updateContent() {
        if (getIsLoading())
            return; // Don't update content while loading

        if (icon != null) {
            var content = new RRHBox(8);
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

        if (isSquare.get())
            styleClass.add("square");

        if (isOutlined.get())
            styleClass.add("outlined");

        if (isFlat.get())
            styleClass.add("flat");

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
