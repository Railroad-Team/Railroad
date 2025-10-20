package dev.railroadide.railroad.window;

import dev.railroadide.core.ui.RRButton;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DialogBuilder extends AlertBuilder<DialogBuilder> {
    private Runnable onConfirm = () -> {};
    private Runnable onCancel = () -> {};

    public static DialogBuilder create() {
        return new DialogBuilder();
    }

    public DialogBuilder onConfirm(Runnable onConfirm) {
        this.onConfirm = onConfirm == null ? () -> {} : onConfirm;
        return this;
    }

    public DialogBuilder onCancel(Runnable onCancel) {
        this.onCancel = onCancel == null ? () -> {} : onCancel;
        return this;
    }

    @Override
    public Scene buildScene() {
        Scene scene = super.buildScene();

        HBox buttonsBox = (HBox) ((VBox) ((StackPane) scene.getRoot()).getChildren().getFirst()).getChildren().get(2);
        buttonsBox.getChildren().clear();

        var confirmButton = new RRButton("railroad.generic.confirm");
        confirmButton.setVariant(RRButton.ButtonVariant.SUCCESS);
        var cancelButton = new RRButton("railroad.generic.cancel");
        cancelButton.setVariant(RRButton.ButtonVariant.DANGER);
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
