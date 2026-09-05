package dev.railroadide.railroad.window;

import dev.railroadide.railroad.ui.RRButton;
import dev.railroadide.railroad.ui.styling.ButtonVariant;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Builds an alert-style dialog with confirmation and cancellation callbacks.
 * Supports replacing the message with a custom node and supplying custom buttons.
 */
public class DialogBuilder extends AlertBuilder<DialogBuilder> {
    private Runnable onConfirm = () -> {
    };
    private Runnable onCancel = () -> {
    };
    private Node customContent;
    private List<Node> customButtons;
    private boolean buttonsOverridden;

    /**
     * Creates a dialog builder with submission by Enter disabled.
     */
    public DialogBuilder() {
        submitOnEnter(false);
    }

    /**
     * Creates a dialog builder with default settings.
     *
     * @return a new dialog builder
     */
    public static DialogBuilder create() {
        return new DialogBuilder();
    }

    /**
     * Sets the callback invoked by the default confirm button before closing the stage.
     *
     * @param onConfirm the confirmation callback, or null for no action
     * @return this builder
     */
    public DialogBuilder onConfirm(Runnable onConfirm) {
        this.onConfirm = onConfirm == null ? () -> {
        } : onConfirm;
        return this;
    }

    /**
     * Sets the cancellation callback used with the default buttons.
     * The cancel button invokes this callback directly; dismissal invokes the inherited close
     * callback first. Custom buttons bypass this callback wiring.
     *
     * @param onCancel the cancellation callback, or null for no action
     * @return this builder
     */
    public DialogBuilder onCancel(Runnable onCancel) {
        this.onCancel = onCancel == null ? () -> {
        } : onCancel;
        return this;
    }

    /**
     * Sets a custom node to replace the alert message.
     *
     * @param content the custom content, or null to retain the text message
     * @return this builder
     */
    public DialogBuilder contentNode(Node content) {
        this.customContent = content;
        return this;
    }

    /**
     * Replaces the default confirm and cancel buttons with caller-configured nodes.
     * Custom buttons must provide their own action and close handlers. Null entries are ignored.
     *
     * @param buttons the replacement buttons; null or an empty array removes all buttons
     * @return this builder
     */
    public DialogBuilder buttons(Node... buttons) {
        this.buttonsOverridden = true;
        if (buttons == null) {
            this.customButtons = List.of();
        } else {
            this.customButtons = Arrays.stream(buttons)
                .filter(Objects::nonNull)
                .toList();
        }
        return this;
    }

    /**
     * Creates a dialog scene with custom content and either custom or default buttons.
     * Default buttons invoke their respective callbacks before closing the containing stage.
     *
     * @return a new dialog scene ready to attach to a stage
     */
    @Override
    public Scene buildScene() {
        Scene scene = super.buildScene();

        VBox card = (VBox) ((StackPane) scene.getRoot()).getChildren().getFirst();
        if (customContent != null) {
            customContent.getStyleClass().add("alert-content");
            card.getChildren().set(1, customContent);
        }

        HBox buttonsBox = (HBox) card.getChildren().get(2);
        buttonsBox.getChildren().clear();

        if (buttonsOverridden) {
            if (customButtons != null && !customButtons.isEmpty()) {
                buttonsBox.getChildren().addAll(customButtons);
            }
            return scene;
        }

        var confirmButton = new RRButton("railroad.generic.confirm");
        confirmButton.setVariant(ButtonVariant.SUCCESS);
        var cancelButton = new RRButton("railroad.generic.cancel");
        cancelButton.setVariant(ButtonVariant.DANGER);
        buttonsBox.getChildren().addAll(confirmButton, cancelButton);

        confirmButton.setOnAction(event -> {
            onConfirm.run();
            ((Stage) scene.getWindow()).close();
        });

        cancelButton.setOnAction(event -> {
            onCancel.run();
            ((Stage) scene.getWindow()).close();
        });

        Runnable currentOnClose = this.onClose;
        this.onClose = () -> {
            currentOnClose.run();
            onCancel.run();
        };

        return scene;
    }
}
