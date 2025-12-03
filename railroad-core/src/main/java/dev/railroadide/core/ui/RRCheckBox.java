package dev.railroadide.core.ui;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome6.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.utility.ServiceLocator;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.util.Duration;
import lombok.Getter;

public class RRCheckBox extends CheckBox {

    private FontIcon icon;

    @Getter
    private boolean isLoading = false;
    private String localizationKey;
    private Object[] localizationArgs;

    private String originalText;
    private Node originalGraphic;
    private FontIcon loadingSpinner;

    public RRCheckBox() {
        this("");
    }

    public RRCheckBox(String localizationKey, Ikon icon, Object... args) {
        super((localizationKey != null && !localizationKey.isBlank()) ? ServiceLocator.getService(LocalizationService.class).get(localizationKey) : "");
        setIcon(icon);
        initialize();
        if(localizationKey != null && !localizationKey.isBlank()) {
            this.localizationKey = localizationKey;
            this.localizationArgs = args;
            addLocalizationListener();
        }
    }

    public RRCheckBox(String localizationKey, Node graphic, Object... args) {
        super((localizationKey != null && !localizationKey.isBlank()) ? ServiceLocator.getService(LocalizationService.class).get(localizationKey) : "");
        setGraphic(graphic);
        initialize();
        if(localizationKey != null && !localizationKey.isBlank()) {
            this.localizationKey = localizationKey;
            this.localizationArgs = args;
            addLocalizationListener();
        }
    }

    public RRCheckBox(String localizationKey, Object... args) {
        super(ServiceLocator.getService(LocalizationService.class).get(localizationKey, args));
        initialize();
        this.localizationKey = localizationKey;
        this.localizationArgs = args;
        addLocalizationListener();
    }

    private void initialize() {
        getStyleClass().setAll("rr-check-box", "check-box");
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

        updateContent();
    }

    private void addLocalizationListener() {
        if (localizationKey != null) {
            ServiceLocator.getService(LocalizationService.class).currentLanguageProperty().addListener((observable, oldValue, newValue) -> {
                if (!isLoading) {
                    setText(ServiceLocator.getService(LocalizationService.class).get(localizationKey, localizationArgs));
                }
            });
        }
    }

    /**
     * Set the checkbox text using a localization key with optional formatting arguments.
     * The text will automatically update when the application language changes.
     *
     * @param localizationKey the localization key for the text
     * @param args            optional formatting arguments for the localized text
     */
    public void setLocalizedText(String localizationKey, Object... args) {
        this.localizationKey = localizationKey;
        this.localizationArgs = args;
        if (!isLoading) {
            setText(ServiceLocator.getService(LocalizationService.class).get(localizationKey, args));
        }

        addLocalizationListener();
    }

    /**
     * Set an icon for the checkbox
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
        if (this.isLoading == loading)
            return;

        this.isLoading = loading;

        if (loading) {
            originalText = getText();
            originalGraphic = getGraphic();

            setDisable(true);
            getStyleClass().add("loading");

            var loadingContent = new RRHBox(8);
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

    private void updateContent() {
        if (isLoading)
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
    
}
