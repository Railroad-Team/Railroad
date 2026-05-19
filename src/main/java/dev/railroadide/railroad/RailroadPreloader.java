package dev.railroadide.railroad;

import javafx.application.Platform;
import javafx.application.Preloader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class RailroadPreloader extends Preloader {
    private Stage stage;
    private Label messageLabel;
    private ProgressBar progressBar;

    @Override
    public void start(Stage primaryStage) {
        stage = primaryStage;
        stage.initStyle(StageStyle.TRANSPARENT);
        stage.setResizable(false);

        var titleLabel = new Label("Railroad");
        titleLabel.getStyleClass().add("preloader-title");

        var subtitleLabel = new Label("Booting the workbench");
        subtitleLabel.getStyleClass().add("preloader-subtitle");

        messageLabel = new Label("Starting Railroad...");
        messageLabel.getStyleClass().add("preloader-message");

        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("preloader-progress");

        var content = new VBox(titleLabel, subtitleLabel, messageLabel, progressBar);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("preloader-content");
        content.setEffect(new DropShadow(40, Color.color(0, 0, 0, 0.45)));

        var root = new StackPane(content);
        root.getStyleClass().add("preloader-root");

        var scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        var stylesheet = AppResources.getResource("styles/preloader.css");
        if (stylesheet != null) {
            scene.getStylesheets().add(stylesheet.toExternalForm());
        }
        stage.setScene(scene);
        stage.sizeToScene();
        stage.show();
        stage.centerOnScreen();
        Platform.runLater(this::centerStage);
        stage.widthProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(this::centerStage));
        stage.heightProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(this::centerStage));
    }

    @Override
    public void handleApplicationNotification(PreloaderNotification notification) {
        if (notification instanceof StatusNotification(String message, double progress)) {
            messageLabel.setText(message);
            progressBar.setProgress(Math.max(0, Math.min(1, progress)));
        } else if (notification instanceof ErrorNotification(String message)) {
            messageLabel.setText(message);
            progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        }
    }

    @Override
    public void handleStateChangeNotification(StateChangeNotification notification) {
        if (notification.getType() == StateChangeNotification.Type.BEFORE_START && stage != null) {
            stage.hide();
        }
    }

    private void centerStage() {
        if (stage == null) return;

        var bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX() + Math.max(0, (bounds.getWidth() - stage.getWidth()) / 2));
        stage.setY(bounds.getMinY() + Math.max(0, (bounds.getHeight() - stage.getHeight()) / 2));
    }

    public record StatusNotification(String message, double progress) implements PreloaderNotification {
    }

    public record ErrorNotification(String message) implements PreloaderNotification {
    }
}
