package dev.railroadide.railroad.window;

import dev.railroadide.core.ui.RRButton;
import dev.railroadide.core.ui.styling.ButtonVariant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DialogBuilder extends AlertBuilder<DialogBuilder> {
    private Runnable onConfirm = () -> {
    };
    private Runnable onCancel = () -> {
    };
    private Node customContent;
    private List<Node> customButtons;
    private boolean buttonsOverridden;

    public DialogBuilder() {
        submitOnEnter(false);
    }

    public static DialogBuilder create() {
        return new DialogBuilder();
    }

    public DialogBuilder onConfirm(Runnable onConfirm) {
        this.onConfirm = onConfirm == null ? () -> {
        } : onConfirm;
        return this;
    }

    public DialogBuilder onCancel(Runnable onCancel) {
        this.onCancel = onCancel == null ? () -> {
        } : onCancel;
        return this;
    }

    public DialogBuilder contentNode(Node content) {
        this.customContent = content;
        return this;
    }

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
