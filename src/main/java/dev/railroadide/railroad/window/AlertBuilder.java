package dev.railroadide.railroad.window;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.RRHBox;
import dev.railroadide.core.ui.RRStackPane;
import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.railroad.AppResources;
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

@SuppressWarnings("unchecked")
public class AlertBuilder<T extends AlertBuilder<?>> {
    protected String title = "";
    protected boolean translateTitle = true;
    protected String content = "";
    protected boolean translateContent = true;
    protected Runnable onClose = () -> {};
    protected AlertType alertType = AlertType.INFO;

    public static AlertBuilder<?> create() {
        return new AlertBuilder<>();
    }

    public T title(String title, boolean translate) {
        this.title = title;
        this.translateTitle = translate;
        return (T) this;
    }

    public T title(String title) {
        return title(title, true);
    }

    public T translateTitle(boolean translate) {
        this.translateTitle = translate;
        return (T) this;
    }

    public T content(String content, boolean translate) {
        this.content = content;
        this.translateContent = translate;
        return (T) this;
    }

    public T content(String content) {
        return content(content, true);
    }

    public T translateContent(boolean translate) {
        this.translateContent = translate;
        return (T) this;
    }

    public T onClose(Runnable onClose) {
        this.onClose = onClose == null ? () -> {} : onClose;
        return (T) this;
    }

    public T alertType(AlertType alertType) {
        this.alertType = alertType;
        return (T) this;
    }

    public Scene buildScene() {
        var overlay = new RRStackPane();
        overlay.getStyleClass().add("alert-overlay");
        overlay.setPadding(new Insets(24));

        var card = new RRVBox(18);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(24));
        card.getStyleClass().addAll("alert-card", "alert-" + alertType.name().toLowerCase());

        var header = new RRHBox(12);
        header.setAlignment(Pos.CENTER_LEFT);

        var iconWrap = new RRStackPane();
        iconWrap.setMinSize(32, 32);
        iconWrap.setPrefSize(32, 32);
        iconWrap.getStyleClass().add("alert-icon-wrap");
        var iconBg = new Circle(16);
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
        secondary.setVariant(RRButton.ButtonVariant.SECONDARY);

        var primary = new RRButton(switch (alertType) {
            case INFO, SUCCESS, ERROR -> "railroad.generic.ok";
            case WARNING -> "railroad.generic.proceed";
        });
        primary.setVariant(RRButton.ButtonVariant.PRIMARY);
        primary.setDefaultButton(true);

        buttons.getChildren().addAll(secondary, primary);

        card.getChildren().addAll(header, contentFlow, buttons);

        overlay.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);

        Runnable close = () -> {
            try {
                onClose.run();
            } catch (Exception ignored) {}

            var window = overlay.getScene() != null ? overlay.getScene().getWindow() : null;
            if (window instanceof Stage stage) {
                stage.close();
            } else if (window != null) {
                window.hide();
            }
        };

        primary.setOnAction($ -> close.run());
        secondary.setOnAction($ -> close.run());

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
            } else if (event.getCode() == KeyCode.ENTER) {
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
