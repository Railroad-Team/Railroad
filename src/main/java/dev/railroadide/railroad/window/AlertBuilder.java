package dev.railroadide.railroad.window;

import dev.railroadide.railroad.AppResources;
import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.RRHBox;
import dev.railroadide.railroad.ui.RRStackPane;
import dev.railroadide.railroad.ui.RRVBox;
import dev.railroadide.railroad.ui.localized.LocalizedLabel;
import dev.railroadide.railroad.ui.localized.LocalizedText;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import io.github.palexdev.mfxresources.fonts.MFXFontIcon;
import io.github.palexdev.mfxresources.fonts.fontawesome.FontAwesomeSolid;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.util.Duration;

// TODO: Add a way to make the content translatable with arguments
/**
 * Builds a styled alert scene with a title, message, and dismissal buttons.
 * Titles and content are translated by default, and Enter submits the alert.
 *
 * @param <T> the builder type returned by fluent configuration methods
 */
@SuppressWarnings("unchecked")
public class AlertBuilder<T extends AlertBuilder<?>> {
    /** The alert heading text or translation key. */
    protected String title = "";
    /** Whether the heading is interpreted as a translation key. */
    protected boolean translateTitle = true;
    /** The message text or translation key. */
    protected String content = "";
    /** Whether the message is interpreted as a translation key. */
    protected boolean translateContent = true;
    /** The callback invoked when the alert is dismissed. */
    protected Runnable onClose = () -> {
    };
    /** The severity controlling the icon, styling, and button labels. */
    protected AlertType alertType = AlertType.INFO;
    /** Whether Enter activates the primary button. */
    protected boolean submitOnEnter = true;

    /**
     * Creates an informational alert builder with default settings.
     *
     * @return a new alert builder
     */
    public static AlertBuilder<?> create() {
        return new AlertBuilder<>();
    }

    /**
     * Sets the alert heading and whether it should be translated.
     *
     * @param title the heading text or translation key
     * @param translate whether to translate the heading
     * @return this builder
     */
    public T title(String title, boolean translate) {
        this.title = title;
        this.translateTitle = translate;
        return (T) this;
    }

    /**
     * Sets the alert heading and enables translation.
     *
     * @param title the heading translation key
     * @return this builder
     */
    public T title(String title) {
        return title(title, true);
    }

    /**
     * Sets whether the alert heading is translated.
     *
     * @param translate whether to interpret the heading as a translation key
     * @return this builder
     */
    public T translateTitle(boolean translate) {
        this.translateTitle = translate;
        return (T) this;
    }

    /**
     * Sets the alert message and whether it should be translated.
     *
     * @param content the message text or translation key
     * @param translate whether to translate the message
     * @return this builder
     */
    public T content(String content, boolean translate) {
        this.content = content;
        this.translateContent = translate;
        return (T) this;
    }

    /**
     * Sets the alert message and enables translation.
     *
     * @param content the message translation key
     * @return this builder
     */
    public T content(String content) {
        return content(content, true);
    }

    /**
     * Sets whether the alert message is translated.
     *
     * @param translate whether to interpret the message as a translation key
     * @return this builder
     */
    public T translateContent(boolean translate) {
        this.translateContent = translate;
        return (T) this;
    }

    /**
     * Sets the callback for dismissal by a button, Escape, or a window close request.
     *
     * @param onClose the dismissal callback, or null for no action
     * @return this builder
     */
    public T onClose(Runnable onClose) {
        this.onClose = onClose == null ? () -> {
        } : onClose;
        return (T) this;
    }

    /**
     * Sets the severity used for the alert icon, styling, and button labels.
     *
     * @param alertType the alert severity
     * @return this builder
     */
    public T alertType(AlertType alertType) {
        this.alertType = alertType;
        return (T) this;
    }

    /**
     * Sets whether Enter activates the primary alert button.
     *
     * @param submitOnEnter whether to submit when Enter is pressed
     * @return this builder
     */
    public T submitOnEnter(boolean submitOnEnter) {
        this.submitOnEnter = submitOnEnter;
        return (T) this;
    }

    /**
     * Creates the styled alert scene with dismissal handlers and an entrance animation.
     * Both buttons and Escape invoke the close callback before closing or hiding the window.
     *
     * @return a new alert scene ready to attach to a window
     */
    public Scene buildScene() {
        var overlay = new RRStackPane();
        overlay.getStyleClass().add("alert-overlay");

        var card = new RRVBox();
        card.setAlignment(Pos.TOP_LEFT);
        card.getStyleClass().addAll("alert-card", "alert-" + alertType.name().toLowerCase());

        var header = new RRHBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("alert-header");

        var iconWrap = new RRStackPane();
        iconWrap.getStyleClass().add("alert-icon-wrap");
        var iconBg = new Circle();
        iconBg.getStyleClass().add("alert-icon-bg");

        var fontIcon = new MFXFontIcon(switch (alertType) {
            case INFO -> FontAwesomeSolid.CIRCLE_INFO;
            case SUCCESS -> FontAwesomeSolid.CIRCLE_CHECK;
            case WARNING -> FontAwesomeSolid.TRIANGLE_EXCLAMATION;
            case ERROR -> FontAwesomeSolid.CIRCLE_XMARK;
        });
        fontIcon.setSize(16);
        fontIcon.getStyleClass().add("alert-icon");
        iconWrap.getChildren().setAll(iconBg, fontIcon);

        var titleLbl = translateTitle ? new LocalizedLabel(title) : new Label(title);
        titleLbl.getStyleClass().add("alert-title");

        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(iconWrap, titleLbl, spacer);

        var contentFlow = new TextFlow(translateContent ? new LocalizedText(content) : new Text(content));
        contentFlow.setTextAlignment(TextAlignment.LEFT);
        contentFlow.getStyleClass().add("alert-content");

        var buttons = new RRHBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        buttons.getStyleClass().add("alert-buttons");

        var secondary = new RRButton(switch (alertType) {
            case WARNING, ERROR -> "railroad.generic.dismiss";
            default -> "railroad.generic.cancel";
        });
        secondary.setVariant(ButtonVariant.SECONDARY);

        var primary = new RRButton(switch (alertType) {
            case INFO, SUCCESS, ERROR -> "railroad.generic.ok";
            case WARNING -> "railroad.generic.proceed";
        });
        primary.setVariant(ButtonVariant.PRIMARY);
        primary.setDefaultButton(submitOnEnter);

        buttons.getChildren().addAll(secondary, primary);

        card.getChildren().addAll(header, contentFlow, buttons);

        overlay.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);

        Runnable close = () -> {
            try {
                onClose.run();
            } catch (Exception _) {
            }

            var window = overlay.getScene() != null ? overlay.getScene().getWindow() : null;
            if (window instanceof Stage stage) {
                stage.close();
            } else if (window != null) {
                window.hide();
            }
        };

        primary.setOnAction(_ -> close.run());
        secondary.setOnAction(_ -> close.run());

        var scene = new Scene(overlay);
        scene.setFill(Color.TRANSPARENT);
        var stylesheet = AppResources.getResource("styles/alert.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }

        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                close.run();
            } else if (submitOnEnter && event.getCode() == KeyCode.ENTER) {
                event.consume();
                primary.fire();
            }
        });

        card.setOpacity(0);
        card.setScaleX(0.985);
        card.setScaleY(0.985);
        Platform.runLater(() -> {
            var fade = new FadeTransition(Duration.millis(130), card);
            fade.setFromValue(0);
            fade.setToValue(1);

            var scale = new ScaleTransition(Duration.millis(130), card);
            scale.setFromX(0.985);
            scale.setFromY(0.985);
            scale.setToX(1.0);
            scale.setToY(1.0);
            scale.setInterpolator(Interpolator.EASE_OUT);

            fade.play();
            scale.play();
        });

        scene.focusOwnerProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null || isAncestorOf(card, newValue)) {
                Platform.runLater(primary::requestFocus);
            }
        });

        scene.windowProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                newValue.setOnCloseRequest(event -> this.onClose.run());
            }
        });

        return scene;
    }

    private static boolean isAncestorOf(Parent potentialAncestor, Node node) {
        Parent parent = node.getParent();
        while (parent != null) {
            if (parent == potentialAncestor)
                return true;

            parent = parent.getParent();
        }

        return false;
    }
}
