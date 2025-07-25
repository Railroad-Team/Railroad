package io.github.railroad.ide;

import io.github.railroad.Railroad;
import io.github.railroad.ui.defaults.RRVBox;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.util.Objects;

public class ImgViewer extends Pane{
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

    public ImgViewer(Path imagePath) {
        ImageView img = new ImageView();

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
        getChildren().add(canvas);
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
        if(mouseEvent.isPrimaryButtonDown()) {
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
            if(scaleFactor <= 4.4) {
                scaleFactor *= 1.1f;

            }
        }else {
            if(scaleFactor >= 0.1) {
                scaleFactor /= 1.1f;

            }
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

    public void setScaleFactor(float scale) {
        this.scaleFactor = scale;
    }


    public void setOffsets(float x, float y) {
        this.xOffset = x;
        this.yOffset = y;
    }

    @Override
    public InputStream getLogo() {
        return LOGO;
    }

    @Override
    public String getPaneName() {
        return "Image Viewer";
    }
}
