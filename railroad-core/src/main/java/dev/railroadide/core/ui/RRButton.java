package dev.railroadide.core.ui;

import dev.railroadide.core.localization.LocalizationServiceLocator;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import lombok.Getter;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A modern button component with enhanced styling, variants, and smooth animations.
 * Supports different sizes, styles, and icon integration.
 */
public class RRButton extends Button {
    private ButtonVariant variant = ButtonVariant.PRIMARY;
    private ButtonSize size = ButtonSize.MEDIUM;
    private FontIcon icon;
    @Getter
    private boolean isLoading = false;
    private String localizationKey;
    private Object[] localizationArgs;

    private String originalText;
    private Node originalGraphic;
    private FontIcon loadingSpinner;

    public RRButton() {
        this("");
    }

    public RRButton(String localizationKey, Ikon icon, Object... args) {
        super(LocalizationServiceLocator.getInstance().get(localizationKey));
        setIcon(icon);
        initialize();
        this.localizationKey = localizationKey;
        this.localizationArgs = args;
        addLocalizationListener();
    }

    public RRButton(String localizationKey, Node graphic, Object... args) {
        super(LocalizationServiceLocator.getInstance().get(localizationKey), graphic);
        initialize();
        this.localizationKey = localizationKey;
        this.localizationArgs = args;
        addLocalizationListener();
    }

    public RRButton(String localizationKey, Object... args) {
        super(LocalizationServiceLocator.getInstance().get(localizationKey, args));
        initialize();
        this.localizationKey = localizationKey;
        this.localizationArgs = args;
        addLocalizationListener();
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

    private void initialize() {
        getStyleClass().addAll("rr-button", "button");
        setAlignment(Pos.CENTER);
        setPadding(new Insets(8, 16, 8, 16));

        loadingSpinner = new FontIcon(FontAwesomeSolid.SYNC_ALT);
        loadingSpinner.setIconSize(16);
        loadingSpinner.getStyleClass().add("loading-spinner");

        setOnMousePressed($ -> {
            if (!isLoading) {
                var scale = new ScaleTransition(Duration.millis(100), this);
                scale.setToX(0.95);
                scale.setToY(0.95);
                scale.play();
            }
        });

        setOnMouseReleased($ -> {
            if (!isLoading) {
                var scale = new ScaleTransition(Duration.millis(100), this);
                scale.setToX(1.0);
                scale.setToY(1.0);
                scale.play();
            }
        });

        updateStyle();
        updateContent();
    }

    private void addLocalizationListener() {
        if (localizationKey != null) {
            LocalizationServiceLocator.getInstance().currentLanguageProperty().addListener((observable, oldValue, newValue) -> {
                if (!isLoading) {
                    setText(LocalizationServiceLocator.getInstance().get(localizationKey, localizationArgs));
                }
            });
        }
    }

    /**
     * Set the button text using a localization key with optional formatting arguments.
     * The text will automatically update when the application language changes.
     *
     * @param localizationKey the localization key for the text
     * @param args optional formatting arguments for the localized text
     */
    public void setLocalizedText(String localizationKey, Object... args) {
        this.localizationKey = localizationKey;
        this.localizationArgs = args;
        if (!isLoading) {
            setText(LocalizationServiceLocator.getInstance().get(localizationKey, args));
        }

        addLocalizationListener();
    }

    /**
     * Set the button variant
     */
    public void setVariant(ButtonVariant variant) {
        this.variant = variant;
        updateStyle();
    }

    /**
     * Set the button size
     */
    public void setButtonSize(ButtonSize size) {
        this.size = size;
        updateStyle();
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
        } else {
            icon = null;
        }

        if (!isLoading) {
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
        if (this.isLoading == loading)
            return;

        this.isLoading = loading;

        if (loading) {
            originalText = getText();
            originalGraphic = getGraphic();

            setDisable(true);
            getStyleClass().add("loading");

            HBox loadingContent = new HBox(8);
            loadingContent.setAlignment(Pos.CENTER);
            loadingContent.getChildren().addAll(loadingSpinner);

            if (originalText != null && !originalText.isEmpty()) {
                setText("Loading...");
            } else {
                setText("");
            }

            setGraphic(loadingContent);
        } else {
            setDisable(false);
            getStyleClass().remove("loading");

            if (originalText != null) {
                setText(originalText);
            }

            if (originalGraphic != null) {
                setGraphic(originalGraphic);
            } else {
                updateContent();
            }
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
     * Set the button as outlined
     */
    public void setOutlined(boolean outlined) {
        if (outlined) {
            getStyleClass().add("outlined");
        } else {
            getStyleClass().remove("outlined");
        }
    }

    /**
     * Set the button as flat
     */
    public void setFlat(boolean flat) {
        if (flat) {
            getStyleClass().add("flat");
        } else {
            getStyleClass().remove("flat");
        }
    }

    private void updateContent() {
        if (isLoading)
            return; // Don't update content while loading

        if (icon != null) {
            HBox content = new HBox(8);
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
        getStyleClass().removeAll("primary", "secondary", "ghost", "danger", "success", "warning");
        getStyleClass().removeAll("small", "medium", "large");

        switch (variant) {
            case PRIMARY -> getStyleClass().add("primary");
            case SECONDARY -> getStyleClass().add("secondary");
            case GHOST -> getStyleClass().add("ghost");
            case DANGER -> getStyleClass().add("danger");
            case SUCCESS -> getStyleClass().add("success");
            case WARNING -> getStyleClass().add("warning");
        }

        switch (size) {
            case SMALL -> getStyleClass().add("small");
            case MEDIUM -> getStyleClass().add("medium");
            case LARGE -> getStyleClass().add("large");
        }
    }

    public enum ButtonVariant {
        PRIMARY, SECONDARY, GHOST, DANGER, SUCCESS, WARNING
    }

    public enum ButtonSize {
        SMALL, MEDIUM, LARGE
    }
}