package dev.railroadide.railroad;

import javafx.application.Platform;
import javafx.application.Preloader;
import javafx.geometry.Insets;
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
        titleLabel.setStyle("-fx-font-size: 30px; -fx-font-weight: 800; -fx-text-fill: white;");

        var subtitleLabel = new Label("Booting the workbench");
        subtitleLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: rgba(255,255,255,0.75);");

        messageLabel = new Label("Starting Railroad...");
        messageLabel.setStyle("-fx-font-size: 15px; -fx-text-fill: #f4f4f8;");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(360);
        progressBar.setStyle("""
            -fx-accent: #ff851a;
            -fx-control-inner-background: rgba(16,16,24,0.55);
            -fx-background-radius: 999;
            -fx-padding: 6;
            """);

        var content = new VBox(12, titleLabel, subtitleLabel, messageLabel, progressBar);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setStyle("""
            -fx-background-radius: 24;
            -fx-border-radius: 24;
            -fx-border-width: 1;
            -fx-border-color: rgba(255,255,255,0.25);
            -fx-background-color: linear-gradient(to bottom right, rgba(33, 40, 62, 0.95), rgba(16, 18, 27, 0.96));
            -fx-padding: 32 36 32 36;
            """);
        content.setEffect(new DropShadow(40, Color.color(0, 0, 0, 0.45)));

        var root = new StackPane(content);
        root.setPadding(new Insets(32));
        root.setStyle("-fx-background-color: transparent;");

        var scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
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
