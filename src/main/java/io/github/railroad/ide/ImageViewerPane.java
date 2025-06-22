package io.github.railroad.ide;

import io.github.railroad.ui.defaults.RRVBox;
import io.github.railroad.utility.FileHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class ImageViewerPane extends BorderPane {
    private Canvas canvas;
    private GraphicsContext gc;

    private Image currentImage;
    private Path imagePath;
    private boolean isPngSource = false;
    private boolean includesCheckerboard = true;

    private double zoomLevel = 1.0;
    private double offsetX = 0.0;
    private double offsetY = 0.0;

    private double lastMouseX;
    private double lastMouseY;

    private static final double ZOOM_BUTTON_FACTOR = 1.2;
    private static final double ZOOM_SCROLL_FACTOR = 1.1;
    private static final double MIN_ZOOM = 0.05;
    private static final double MAX_ZOOM = 20.0;

    private static final int CHECKER_SIZE = 16;
    private static final Color CHECKER_COLOR_1 = Color.rgb(210, 210, 210);
    private static final Color CHECKER_COLOR_2 = Color.rgb(240, 240, 240);
    private static final Color DEFAULT_BACKGROUND_COLOR = Color.web("#333");

    private VBox infoPane;
    private Text dimensionsText;
    private Text fileNameText;
    private Text fileSizeText;
    private Text typeText;
    private Text colorDepthText;
    private Text colorSpaceText;
    private Text numberOfColorsText;

    private StackPane canvasContainer;

    public ImageViewerPane(Path imagePath) {
        super();
        initComponents();

        if (imagePath != null && Files.exists(imagePath)) {
            boolean isPng = imagePath.toString().toLowerCase().endsWith(".png");
            try {
                this.imagePath = imagePath;
                loadImage(new Image(imagePath.toUri().toURL().toString()), isPng);
            } catch (MalformedURLException e) {
                System.err.println("Error loading image: " + e.getMessage());
                loadImage(null);
                this.imagePath = null;
            }
        }
    }

    private void initComponents() {
        canvas = new Canvas();
        gc = canvas.getGraphicsContext2D();

        infoPane = new RRVBox(5);
        infoPane.setPadding(new Insets(10));
        infoPane.setStyle("-fx-background-color: #444444ee; -fx-border-color: #444; -fx-border-width: 1px;");
        infoPane.setVisible(false);
        infoPane.setMouseTransparent(true);
        infoPane.setMaxWidth(VBox.USE_PREF_SIZE);
        infoPane.setMaxHeight(VBox.USE_PREF_SIZE);

        dimensionsText = new Text("Dimensions: ");
        fileNameText = new Text("File Name: ");
        fileSizeText = new Text("File Size: ");
        typeText = new Text("Type: ");
        colorDepthText = new Text("Color Depth: ");
        colorSpaceText = new Text("Color Space: ");
        numberOfColorsText = new Text("Number of Colors: ");
        infoPane.getChildren().addAll(dimensionsText, fileNameText, fileSizeText, typeText, colorDepthText, colorSpaceText, numberOfColorsText);

        canvasContainer = new StackPane(canvas, infoPane);
        canvasContainer.setStyle("-fx-background-color: #333;");

        StackPane.setAlignment(infoPane, Pos.TOP_LEFT);
        StackPane.setMargin(infoPane, new Insets(10));

        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());

        canvas.widthProperty().addListener((obs, oldVal, newVal) -> {
            if (currentImage != null && zoomLevel > 0) {
                double oldCanvasW = oldVal.doubleValue();
                double newCanvasW = newVal.doubleValue();
                if (oldCanvasW > 0) { // Skip initial sizing from 0
                    double centerX = offsetX + (oldCanvasW / 2) / zoomLevel;
                    offsetX = centerX - (newCanvasW / 2) / zoomLevel;
                    clampOffsets();
                }
                redrawCanvas();
            }
        });

        canvas.heightProperty().addListener((obs, oldVal, newVal) -> {
            if (currentImage != null && zoomLevel > 0) {
                double oldCanvasH = oldVal.doubleValue();
                double newCanvasH = newVal.doubleValue();
                if (oldCanvasH > 0) { // Skip initial sizing from 0
                    double centerY = offsetY + (oldCanvasH / 2) / zoomLevel;
                    offsetY = centerY - (newCanvasH / 2) / zoomLevel;
                    clampOffsets();
                }
                redrawCanvas();
            }
        });

        setCenter(canvasContainer);

        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnScroll(this::handleMouseScroll);
    }

    /**
     * Handles mouse press events on the canvas to initiate panning.
     *
     * @param event The mouse event.
     */
    private void handleMousePressed(MouseEvent event) {
        if (currentImage == null)
            return;

        lastMouseX = event.getX();
        lastMouseY = event.getY();

        if (event.isPrimaryButtonDown()) {
            canvas.setCursor(Cursor.CLOSED_HAND);
            applyZoom(ZOOM_BUTTON_FACTOR, event.getX(), event.getY());
        } else if (event.isSecondaryButtonDown()) {
            canvas.setCursor(Cursor.CROSSHAIR);
        } else {
            canvas.setCursor(Cursor.DEFAULT);
        }

        event.consume();
    }

    /**
     * Handles mouse drag events on the canvas for panning.
     *
     * @param event The mouse event.
     */
    private void handleMouseDragged(MouseEvent event) {
        if (currentImage == null || !event.isPrimaryButtonDown()) {
            canvas.setCursor(Cursor.DEFAULT);
            return;
        }

        if (canvas.getCursor() != Cursor.CLOSED_HAND) {
            canvas.setCursor(Cursor.CLOSED_HAND);
        }

        double deltaX = event.getX() - lastMouseX;
        double deltaY = event.getY() - lastMouseY;

        offsetX -= deltaX / zoomLevel;
        offsetY -= deltaY / zoomLevel;

        clampOffsets();
        redrawCanvas();

        lastMouseX = event.getX();
        lastMouseY = event.getY();
    }

    /**
     * Handles mouse release events on the canvas.
     *
     * @param event The mouse event.
     */
    private void handleMouseReleased(MouseEvent event) {
        canvas.setCursor(currentImage == null ? Cursor.DEFAULT : Cursor.OPEN_HAND);
    }

    /**
     * Handles mouse scroll events on the canvas for zooming.
     *
     * @param event The scroll event.
     */
    private void handleMouseScroll(ScrollEvent event) {
        if (currentImage == null)
            return;

        double zoomFactorChange;
        if (event.getDeltaY() > 0) { // Scroll up -> zoom in
            zoomFactorChange = ZOOM_SCROLL_FACTOR;
        } else { // Scroll down -> zoom out
            zoomFactorChange = 1.0 / ZOOM_SCROLL_FACTOR;
        }

        applyZoom(zoomFactorChange, event.getX(), event.getY());
        event.consume();
    }

    /**
     * Applies zoom transformation centered around a pivot point.
     *
     * @param factorChange The factor by which to change the zoom (e.g., 1.1 for 10% zoom in).
     * @param pivotXCanvas The X-coordinate of the zoom pivot on the canvas.
     * @param pivotYCanvas The Y-coordinate of the zoom pivot on the canvas.
     */
    private void applyZoom(double factorChange, double pivotXCanvas, double pivotYCanvas) {
        if (currentImage == null)
            return;

        double oldZoomLevel = zoomLevel;
        double newZoomLevel = oldZoomLevel * factorChange;
        newZoomLevel = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, newZoomLevel));

        if (Math.abs(newZoomLevel - oldZoomLevel) < 1e-6)
            return; // No significant change

        // Determine the point in the image that is currently under the mouse cursor (pivot)
        double imagePivotX = offsetX + pivotXCanvas / oldZoomLevel;
        double imagePivotY = offsetY + pivotYCanvas / oldZoomLevel;

        zoomLevel = newZoomLevel;

        // Adjust offsetX and offsetY so that the imagePivot point remains under the canvasPivot point
        offsetX = imagePivotX - pivotXCanvas / zoomLevel;
        offsetY = imagePivotY - pivotYCanvas / zoomLevel;

        clampOffsets();
        redrawCanvas();
    }

    /**
     * Sets the image to be displayed and resets the view.
     *
     * @param image The image to display. Can be null to clear.
     * @param isPng Indicates if the image is a PNG source.
     */
    public void loadImage(@Nullable Image image, boolean isPng) {
        this.currentImage = image;
        this.isPngSource = isPng;

        boolean isImageValid = false;
        if (this.currentImage != null) {
            if (this.currentImage.isError()) {
                System.err.println("Error loading image: " + this.currentImage.getException().getMessage());
                this.currentImage = null;
                this.isPngSource = false;
            } else {
                isImageValid = true;
            }
        }

        if (isImageValid) {
            infoPane.setVisible(true);
            updateInfoPane();
            resetViewToFitImage();
        } else {
            zoomLevel = 1.0;
            offsetX = 0;
            offsetY = 0;
            redrawCanvas();
            updateInfoPane();
            infoPane.setVisible(false);
        }

        canvas.setCursor(currentImage == null ? Cursor.DEFAULT : Cursor.OPEN_HAND);

        if (canvasContainer != null) canvasContainer.requestLayout();
    }

    private void updateInfoPane() {
        if (this.currentImage != null && this.imagePath != null) {
            dimensionsText.setText(("Dimensions: " + currentImage.getWidth() + " x " + currentImage.getHeight()).replace(".0", ""));
            fileNameText.setText("File Name: " + imagePath.getFileName());
            fileSizeText.setText("File Size: " + FileHandler.humanReadableByteCount(imagePath));
            typeText.setText("Type: " + FileHandler.getExtension(imagePath).toUpperCase(Locale.ROOT));
            colorDepthText.setText("Color Depth: " + FileHandler.getColorDepth(currentImage));
            colorSpaceText.setText("Color Space: " + FileHandler.getColorSpace(currentImage));
            numberOfColorsText.setText("Number of Colors: " + FileHandler.getNumberOfColors(currentImage));
        } else {
            dimensionsText.setText("Dimensions: ? x ?");
            fileNameText.setText("File Name: Unknown");
            fileSizeText.setText("File Size: ?");
            typeText.setText("Type: Unknown");
            colorDepthText.setText("Color Depth: Unknown");
            colorSpaceText.setText("Color Space: Unknown");
            numberOfColorsText.setText("Number of Colors: ?");
        }

        infoPane.requestLayout();
    }

    /**
     * Sets the image to be displayed and resets the view.
     *
     * @param image The image to display. Can be null to clear.
     */
    public void loadImage(@Nullable Image image) {
        loadImage(image, false);
    }

    /**
     * Draws a checkerboard pattern onto the canvas background.
     *
     * @param canvasW The width of the canvas.
     * @param canvasH The height of the canvas.
     */
    private void drawCheckerboardBackground(double canvasW, double canvasH) {
        for (int y = 0; y < canvasH; y += CHECKER_SIZE) {
            for (int x = 0; x < canvasW; x += CHECKER_SIZE) {
                // Determine color based on checker position (row + col)
                boolean isRowEven = (y / CHECKER_SIZE) % 2 == 0;
                boolean isColEven = (x / CHECKER_SIZE) % 2 == 0;
                gc.setFill((isRowEven == isColEven) ? CHECKER_COLOR_1 : CHECKER_COLOR_2);
                gc.fillRect(x, y, CHECKER_SIZE, CHECKER_SIZE);
            }
        }
    }

    /**
     * Resets the zoom and pan to fit the entire image within the canvas.
     * The image will be centered.
     */
    private void resetViewToFitImage() {
        if (currentImage == null || canvas.getWidth() == 0 || canvas.getHeight() == 0) {
            redrawCanvas();
            return;
        }

        double canvasW = canvas.getWidth();
        double canvasH = canvas.getHeight();

        if (currentImage == null || currentImage.isError()) {
            zoomLevel = 1.0;
            offsetX = 0;
            offsetY = 0;
            redrawCanvas();
            updateInfoPane();
            return;
        }

        double imgW = currentImage.getWidth();
        double imgH = currentImage.getHeight();
        if (imgW <= 0 || imgH <= 0) {
            zoomLevel = 1.0;
            offsetX = 0;
            offsetY = 0;
            redrawCanvas();
            return;
        }

        double zoomX = canvasW / imgW;
        double zoomY = canvasH / imgH;
        zoomLevel = Math.min(zoomX, zoomY);
        zoomLevel = Math.clamp(zoomLevel, MIN_ZOOM, MAX_ZOOM);

        offsetX = (imgW - canvasW / zoomLevel) / 2;
        offsetY = (imgH - canvasH / zoomLevel) / 2;

        clampOffsets();
        redrawCanvas();
        updateInfoPane();
    }

    /**
     * Clamps the offsetX and offsetY values to ensure the view stays within
     * the image boundaries.
     */
    private void clampOffsets() {
        if (currentImage == null || zoomLevel == 0)
            return;

        double imgW = currentImage.getWidth();
        double imgH = currentImage.getHeight();
        double canvasW = canvas.getWidth();
        double canvasH = canvas.getHeight();

        // Width/height of the portion of the image that would be visible on canvas
        double visibleImagePortionW = canvasW / zoomLevel;
        double visibleImagePortionH = canvasH / zoomLevel;

        // Max possible X offset: image width - visible portion width
        // If visible portion is wider than image, max offset is 0 (image is smaller than view)
        double maxOffsetX = Math.max(0, imgW - visibleImagePortionW);
        double maxOffsetY = Math.max(0, imgH - visibleImagePortionH);

        offsetX = Math.max(0, Math.min(offsetX, maxOffsetX));
        offsetY = Math.max(0, Math.min(offsetY, maxOffsetY));
    }

    /**
     * Redraws the image on the canvas based on the current zoom level and offsets.
     * This method handles drawing the correct portion of the image and centering
     * it if the scaled image is smaller than the canvas.
     */
    private void redrawCanvas() {
        if (gc == null || canvas == null)
            return;

        double canvasW = canvas.getWidth();
        double canvasH = canvas.getHeight();
        if (canvasW <= 0 || canvasH <= 0)
            return;

        if (isPngSource) {
            drawCheckerboardBackground(canvasW, canvasH);
        } else {
            gc.setFill(DEFAULT_BACKGROUND_COLOR);
            gc.fillRect(0, 0, canvasW, canvasH);
        }

        if (currentImage == null || currentImage.isError() || zoomLevel <= 0)
            return;

        double imgW = currentImage.getWidth();
        double imgH = currentImage.getHeight();

        // Calculate source rectangle
        double srcX = offsetX;
        double srcY = offsetY;
        double srcW = canvasW / zoomLevel;
        double srcH = canvasH / zoomLevel;

        // Calculate destination rectangle
        double destX = 0;
        double destY = 0;
        double destW = canvasW;
        double destH = canvasH;

        // Adjust for centering when image is smaller than canvas view
        double scaledImgW = imgW * zoomLevel;
        double scaledImgH = imgH * zoomLevel;
        if (scaledImgW < canvasW) {
            srcX = 0;
            srcW = imgW;
            destW = scaledImgW;
            destX = (canvasW - destW) / 2;
        }
        if (scaledImgH < canvasH) {
            srcY = 0;
            srcH = imgH;
            destH = scaledImgH;
            destY = (canvasH - destH) / 2;
        }

        // Final clamping/validation of source rectangle
        srcX = Math.max(0, srcX);
        srcY = Math.max(0, srcY);
        srcW = Math.min(srcW, imgW - srcX);
        srcH = Math.min(srcH, imgH - srcY);

        if (srcW <= 0 || srcH <= 0 || destW <= 0 || destH <= 0)
            return;

        gc.setImageSmoothing(zoomLevel < 3.0);

        gc.drawImage(currentImage, srcX, srcY, srcW, srcH, destX, destY, destW, destH);
    }
}
