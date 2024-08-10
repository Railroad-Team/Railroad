package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.ui.defaults.RRBorderPane;
import io.github.railroad.ui.defaults.RRStackPane;
import io.github.railroad.utility.FileHandler;
import io.github.railroad.utility.MathUtils;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import lombok.Getter;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Locale;

@Getter
public class ImageViewerPane extends RRStackPane {
    private final DoubleProperty mouseX = new SimpleDoubleProperty();
    private final DoubleProperty mouseY = new SimpleDoubleProperty();

    private final RRBorderPane borderPane = new RRBorderPane();
    private final Path imagePath;
    private final Image image;
    private final ImageView imageView;

    public ImageViewerPane(Path imagePath) {
        this.imagePath = imagePath;

        this.borderPane.prefWidthProperty().bind(widthProperty());
        this.borderPane.prefHeightProperty().bind(heightProperty());

        try {
            this.image = new Image(this.imagePath.toFile().toURI().toURL().toExternalForm());
        } catch (MalformedURLException exception) {
            Railroad.LOGGER.error("Failed to load image from URL: {}", this.imagePath);
            throw new IllegalArgumentException(exception);
        }

        this.imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(800);
        imageView.setFitHeight(600);
        borderPane.setCenter(imageView);

        imageView.setOnMouseMoved(event -> {
            mouseX.set(event.getX());
            mouseY.set(event.getY());
        });

        imageView.setOnScroll(event -> {
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

        imageView.setOnMousePressed(event -> {
            initialX.set(event.getX());
            initialY.set(event.getY());
        });

        imageView.setOnMouseDragged(event -> {
            double deltaX = event.getX() - initialX.get();
            double deltaY = event.getY() - initialY.get();

            imageView.setTranslateX(imageView.getTranslateX() + deltaX);
            imageView.setTranslateY(imageView.getTranslateY() + deltaY);

            initialX.set(event.getX());
            initialY.set(event.getY());
            imageView.setCursor(Cursor.CLOSED_HAND);
        });

        imageView.setOnMouseReleased(event -> imageView.setCursor(Cursor.DEFAULT));

        getChildren().add(borderPane);
        RRStackPane.setAlignment(borderPane, Pos.CENTER);

        var infoPane = new VBox();
        infoPane.setMouseTransparent(true);
        infoPane.setSpacing(5);
        infoPane.setBackground(new Background(new BackgroundFill(Color.web("#00000040"), new CornerRadii(10), Insets.EMPTY)));
        infoPane.setPadding(new Insets(10));
        infoPane.setMaxSize(0, 0);

        var sizeText = new Text("Size: " + (int) image.getWidth() + "x" + (int) image.getHeight());
        var typeText = new Text("Type: " + FileHandler.getExtension(imagePath).toUpperCase(Locale.ROOT));
        var colorDepthText = new Text("Color Depth: " + FileHandler.getColorDepth(image) + " bit");
        var fileSizeText = new Text("File Size: " + FileHandler.humanReadableByteCount(imagePath));
        var colorSpaceText = new Text("Color Space: " + FileHandler.getColorSpace(image));
        var numberOfColorsText = new Text("Number of Colors: " + FileHandler.getNumberOfColors(image));

        infoPane.getChildren().addAll(sizeText, typeText, colorDepthText, fileSizeText, colorSpaceText, numberOfColorsText);

        if (FileHandler.getExtension(imagePath).equalsIgnoreCase("gif")) {
            var numberOfFramesText = new Text("Number of Frames: " + FileHandler.getNumberOfFrames(imagePath));
            infoPane.getChildren().add(numberOfFramesText);
        }

        getChildren().add(infoPane);
        RRStackPane.setAlignment(infoPane, Pos.TOP_RIGHT);
    }
}
