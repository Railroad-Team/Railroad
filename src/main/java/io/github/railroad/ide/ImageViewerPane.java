package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.ui.defaults.RRHBox;
import io.github.railroad.ui.defaults.RRStackPane;
import io.github.railroad.utility.FileHandler;
import io.github.railroad.utility.MathUtils;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import lombok.Getter;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Locale;

@Getter
public class ImageViewerPane extends RRStackPane {
    private final DoubleProperty mouseX = new SimpleDoubleProperty();
    private final DoubleProperty mouseY = new SimpleDoubleProperty();

    private final Path imagePath;
    private final Image image;
    private final ImageView imageView;

    public ImageViewerPane(Path imagePath) {
        this.imagePath = imagePath;

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
        imageView.setManaged(false);

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
            imageView.setCursor(Cursor.CLOSED_HAND);
        });

        setOnMouseReleased(event -> imageView.setCursor(Cursor.DEFAULT));

        getChildren().add(imageView);
        RRStackPane.setAlignment(imageView, Pos.CENTER);

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

        var controlPane = new RRHBox();
        controlPane.setSpacing(5);
        controlPane.setBackground(new Background(new BackgroundFill(Color.web("#00000040"), new CornerRadii(10), Insets.EMPTY)));
        controlPane.setPadding(new Insets(10));
        controlPane.setMaxSize(0, 0);

        var zoomInIcon = new FontIcon(FontAwesomeSolid.SEARCH_PLUS.getDescription());
        var zoomInButton = new Button("", zoomInIcon);

        var zoomOutIcon = new FontIcon(FontAwesomeSolid.SEARCH_MINUS.getDescription());
        var zoomOutButton = new Button("", zoomOutIcon);

        var resetIcon = new FontIcon(FontAwesomeSolid.WINDOW_RESTORE.getDescription());
        var resetButton = new Button("", resetIcon);

        zoomInButton.setOnAction(event -> {
            double oldWidth = imageView.getFitWidth();
            double oldHeight = imageView.getFitHeight();
            double newWidth = oldWidth * 1.05;
            double newHeight = oldHeight * 1.05;

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
        });

        zoomOutButton.setOnAction(event -> {
            double oldWidth = imageView.getFitWidth();
            double oldHeight = imageView.getFitHeight();
            double newWidth = oldWidth * 0.95;
            double newHeight = oldHeight * 0.95;

            if (newWidth >= 400 || newHeight >= 300) {
                imageView.setFitWidth(newWidth);
                imageView.setFitHeight(newHeight);

                double mouseXInImageView = (mouseX.get() - imageView.getTranslateX()) / oldWidth;
                double mouseYInImageView = (mouseY.get() - imageView.getTranslateY()) / oldHeight;

                double newTranslateX = mouseX.get() - mouseXInImageView * newWidth;
                double newTranslateY = mouseY.get() - mouseYInImageView * newHeight;

                imageView.setTranslateX(newTranslateX);
                imageView.setTranslateY(newTranslateY);
            }
        });

        resetButton.setOnAction(event -> {
            imageView.setFitWidth(800);
            imageView.setFitHeight(600);
            imageView.setTranslateX(0);
            imageView.setTranslateY(0);
        });

        controlPane.getChildren().addAll(zoomInButton, zoomOutButton, resetButton);

        if (FileHandler.isImageTransparent(image)) {
            var showCheckerboardIcon = new FontIcon(FontAwesomeSolid.CHESS_BOARD.getDescription());
            var showCheckerboardButton = new ToggleButton("", showCheckerboardIcon);
            showCheckerboardButton.setSelected(false);

            var checkerboardView = new ImageView();

            checkerboardView.setFitWidth(800);
            checkerboardView.setFitHeight(600);
            checkerboardView.setImage(FileHandler.createCheckerboard(
                    (int) image.getWidth(),
                    (int) image.getHeight(),
                    50,
                    Color.LIGHTGRAY,
                    Color.WHITE));

            showCheckerboardButton.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == null || !newValue) {
                    getChildren().removeFirst();
                } else {
                    getChildren().addFirst(checkerboardView);
                    RRStackPane.setAlignment(checkerboardView, Pos.CENTER);
                }
            });

            controlPane.getChildren().add(showCheckerboardButton);
        }

        getChildren().add(controlPane);
        RRStackPane.setAlignment(controlPane, Pos.TOP_LEFT);
    }
}
