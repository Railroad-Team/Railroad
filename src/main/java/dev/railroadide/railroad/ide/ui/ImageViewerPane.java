package dev.railroadide.railroad.ide.ui;

import dev.railroadide.core.ui.RRVBox;
<<<<<<< HEAD
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.IDESetup;

=======
import dev.railroadide.railroad.utility.FileUtils;
import dev.railroadide.railroad.utility.ImageUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
>>>>>>> 21649082af8d112ef8a40c33a03304cf312efc5b
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Path;

public class ImageViewerPane extends RRVBox {
    public static final InputStream LOGO = Railroad.getResourceAsStream("images/IDEIcons/IMGViewer.png");
    private final Canvas canvas;
    private final GraphicsContext ctx;
    private float scaleFactor = 1.0f;
    private float xOffset;
    private float yOffset;
    private Image current;


    double previousX = 0.0D;
    double previousY = 0.0D;
    double currentX = 0.0D;
    double currentY = 0.0D;

    public ImageViewerPane(Path imagePath) {
        ImageView img = new ImageView();
        javafx.scene.control.Button openInEditor = new javafx.scene.control.Button("Open In Editor");
        openInEditor.setOnAction(actionEvent -> {
            IDESetup.addPane(new ImageEditorPane(imagePath), IDESetup.getEditorPane());
        });
        javafx.scene.control.Button refresh = new Button("Refresh");
        refresh.setOnAction(actionEvent -> {
            drawImage(current);
        });
        canvas = new Canvas();
        ctx = canvas.getGraphicsContext2D();
        canvas.setManaged(false);
        xOffset = (float) (getWidth()/2F - canvas.getWidth()/2F);
        yOffset = (float) (getHeight()/2F - canvas.getHeight()/2F);
        try {
            current = new Image(imagePath.toUri().toURL().toString());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid image path: " + imagePath, e);
        }
        centerCanvas();
        drawImage(current);
        getChildren().addAll(canvas,openInEditor,refresh);
        setOnScroll(this::onScroll);
        widthProperty().addListener((obs, oldVal, newVal) -> {
            xOffset = (float) (getWidth()/2F - canvas.getWidth()/2F);
            centerCanvas();
            drawImage(current);
        });

        heightProperty().addListener((obs, oldVal, newVal) -> {
            yOffset = (float) (getHeight()/2F - canvas.getHeight()/2F);

            centerCanvas();
            drawImage(current);
        });
        setOnMouseDragged(this::onDrag);
        setOnMousePressed(mouseEvent ->{
            this.previousX = mouseEvent.getX();
            this.previousY = mouseEvent.getY();
            this.currentX = mouseEvent.getX();
            this.currentY = mouseEvent.getY();
        });

    }


    public void onDrag(MouseEvent mouseEvent) {
        if(mouseEvent.isPrimaryButtonDown() || mouseEvent.isMiddleButtonDown()) {
            previousX = currentX;
            previousY = currentY;
            currentX = mouseEvent.getX();
            currentY = mouseEvent.getY();

            double dx = currentX - previousX;
            double dy = currentY - previousY;
            xOffset += (float) dx;
            yOffset += (float) dy;
            drawImage(current);
        }
    }
    public void onScroll(ScrollEvent scrollEvent) {
        if(scrollEvent.getDeltaY() > 0) {
                scaleFactor *= 1.1f;


        }else {
                scaleFactor /= 1.1f;
        }
        drawImage(current);

    }
    private void centerCanvas() {
        canvas.setTranslateX(xOffset);
        canvas.setTranslateY(yOffset);
    }

    public void drawImage(Image image) {

        this.current = image;
        double imgWidth =  image.getWidth();
        double imgHeight = image.getHeight();

        // Set canvas size to match scaled image
        double canvasWidth = imgWidth * scaleFactor;
        double canvasHeight = imgHeight * scaleFactor;
        canvas.setWidth(canvasWidth);
        canvas.setHeight(canvasHeight);

        ctx.clearRect(0, 0, canvasWidth, canvasHeight);

        for (int x = 0; x < imgWidth; x++) {
            for (int y = 0; y < imgHeight; y++) {
                Color color = image.getPixelReader().getColor(x, y);
                ctx.setFill(color);

                // Draw a scaled rectangle for each pixel
                double drawX = Math.floor(x * scaleFactor);
                double drawY = Math.floor(y * scaleFactor);
                double drawW = Math.ceil(scaleFactor);
                double drawH = Math.ceil(scaleFactor);
                ctx.fillRect(drawX, drawY, drawW, drawH);
                ctx.fillRect(drawX, drawY, drawW + 0.5, drawH + 0.5);

            }
        }
        centerCanvas();
    }

<<<<<<< HEAD
    public void setScaleFactor(float scale) {
        this.scaleFactor = scale;
=======
    private void updateInfoPane() {
        if (this.currentImage != null && this.imagePath != null) {
            dimensionsText.setText(("Dimensions: " + currentImage.getWidth() + " x " + currentImage.getHeight()).replace(".0", ""));
            fileNameText.setText("File Name: " + imagePath.getFileName());
            fileSizeText.setText("File Size: " + FileUtils.humanReadableByteCount(imagePath));
            typeText.setText("Type: " + FileUtils.getExtension(imagePath).toUpperCase(Locale.ROOT));
            colorDepthText.setText("Color Depth: " + ImageUtils.getColorDepth(currentImage));
            colorSpaceText.setText("Color Space: " + ImageUtils.getColorSpace(currentImage));
            numberOfColorsText.setText("Number of Colors: " + ImageUtils.getNumberOfColors(currentImage));
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
>>>>>>> 21649082af8d112ef8a40c33a03304cf312efc5b
    }


    public void setOffsets(float x, float y) {
        this.xOffset = x;
        this.yOffset = y;
    }

}
