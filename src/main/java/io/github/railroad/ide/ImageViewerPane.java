package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.utility.MathUtils;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Getter
public class ImageViewerPane extends BorderPane {
    private final DoubleProperty mouseX = new SimpleDoubleProperty();
    private final DoubleProperty mouseY = new SimpleDoubleProperty();

    private final Path imagePath;
    private final Image image;
    private final ImageView imageView;

    public ImageViewerPane(Path imagePath) {
        this.imagePath = imagePath;

        try(InputStream imageStream = Files.newInputStream(this.imagePath)) {
            this.image = new Image(imageStream);
            this.imageView = new ImageView(image);
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(800);
            imageView.setFitHeight(600);
            setCenter(imageView);
        } catch (IOException exception) {
            Railroad.LOGGER.error("Failed to load image: {}", this.imagePath, exception);
            throw new IllegalStateException("Failed to load image: " + this.imagePath, exception);
        }

        setOnMouseMoved(event -> {
            mouseX.set(event.getX());
            mouseY.set(event.getY());
        });

        setOnScroll(event -> {
            // zoom into the mouse position
            double zoomFactor = event.getDeltaY() > 0 ? 1.05 : 0.95;
            double oldWidth = imageView.getFitWidth();
            double oldHeight = imageView.getFitHeight();
            double newWidth = oldWidth * zoomFactor;
            double newHeight = oldHeight * zoomFactor;

            if (newWidth <= 800 || newHeight <= 600) {
                newWidth = MathUtils.clamp(newWidth, 400, 800);
                newHeight = MathUtils.clamp(newHeight, 300, 600);
                imageView.setTranslateX(0);
                imageView.setTranslateY(0);

                imageView.setFitWidth(newWidth);
                imageView.setFitHeight(newHeight);
            } else {
                imageView.setFitWidth(newWidth);
                imageView.setFitHeight(newHeight);

                double mouseXInImageView = (mouseX.get() - imageView.getTranslateX()) / oldWidth;
                double mouseYInImageView = (mouseY.get() - imageView.getTranslateY()) / oldHeight;

                double newTranslateX = mouseX.get() - mouseXInImageView * newWidth;
                double newTranslateY = mouseY.get() - mouseYInImageView * newHeight;
                imageView.setTranslateX(newTranslateX);
                imageView.setTranslateY(newTranslateY);
            }

            event.consume();
        });

        final DoubleProperty initialX = new SimpleDoubleProperty();
        final DoubleProperty initialY = new SimpleDoubleProperty();

        setOnMousePressed(event -> {
            initialX.set(event.getX());
            initialY.set(event.getY());
        });

        setOnMouseDragged(event -> {
            double deltaX = event.getX() - initialX.get();
            double deltaY = event.getY() - initialY.get();

            imageView.setTranslateX(imageView.getTranslateX() + deltaX);
            imageView.setTranslateY(imageView.getTranslateY() + deltaY);

            initialX.set(event.getX());
            initialY.set(event.getY());
            setCursor(Cursor.CLOSED_HAND);
        });

        setOnMouseReleased(event -> setCursor(Cursor.DEFAULT));
    }
}
