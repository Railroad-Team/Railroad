package dev.railroadide.railroad.ide.ui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.imageio.ImageIO;

import dev.railroadide.core.ui.RRVBox;
import dev.railroadide.railroad.Railroad;
import dev.railroadide.railroad.ide.images.ColorPalette;
import dev.railroadide.railroad.ide.images.Palette;
import io.github.palexdev.materialfx.utils.SwingFXUtils;


import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;

public class ImageEditorPane extends RRVBox implements PaneInterface {
    public static final InputStream LOGO = Railroad.getResourceAsStream("images/IDEIcons/IMGViewer.png");
    private final Canvas canvas;
    private final GraphicsContext ctx;
    private final ColorPicker colorPicker;
    private final ColorPalette colorPalette;

    private float scaleFactor = 1.0f;
    private float xOffset;
    private float yOffset;

    double mouseX = 0.0D;
    double mouseY = 0.0D;
    double cursorX = 0;
    double cursorY = 0;
    boolean renderCursor = true;
    private WritableImage current;
    private Color altStartColor;
    double previousX = 0.0D;
    double previousY = 0.0D;
    double currentX = 0.0D;
    double currentY = 0.0D;
    private final Button save = new Button("Save");



    public void saveImage(WritableImage image, String filePath) {
        File file = new File(filePath);
        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            System.out.println("Image saved to " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ImageEditorPane(Path imagePath) {


        save.setText("Save");

        save.setOnMouseClicked(mouseEvent -> {
            saveImage(current, imagePath.toString());
            save.setText("Save");
        });

        canvas = new Canvas();

        colorPicker = new ColorPicker();
        colorPicker.setFocusTraversable(false);




        Palette palette = new Palette(List.of(Color.WHITE,Color.BLUE));

        colorPalette = new ColorPalette(palette);
        colorPalette.addEventHandler(ActionEvent.ACTION,actionEvent -> {
            colorPicker.setValue(colorPalette.getSelectedColor());
        });
        colorPicker.setOnAction(e -> {
            if(!colorPalette.contains(colorPicker.getValue())){
                colorPalette.addToPalette(colorPicker.getValue());
            }
        });
        this.ctx = canvas.getGraphicsContext2D();
        canvas.setManaged(false);
        Button resetView = new Button("Reset View");
        resetView.setOnAction(actionEvent -> {
            // Don't Ask Why Looping It Works, IDK
            for (int i = 0; i < 2; i++) {
                scaleFactor=1;

                xOffset = (float) (getWidth() / 2F - canvas.getWidth() / 2F);
                yOffset = (float) (getHeight() / 2F - canvas.getHeight() / 2F);
                drawImage(current);
            }

        });
        xOffset = (float) (getWidth() / 2F - canvas.getWidth() / 2F);
        yOffset = (float) (getHeight() / 2F - canvas.getHeight() / 2F);

        try {
            var _img = new Image(imagePath.toUri().toURL().toString());
            current = new WritableImage(_img.getPixelReader(), (int) _img.getWidth(), (int) _img.getHeight());
        } catch (MalformedURLException e) {
            throw new RuntimeException("Invalid image path: " + imagePath, e);
        }

        centerCanvas();
        drawImage(current);

        getChildren().addAll(canvas, colorPicker, save,resetView,colorPalette);
        setOnScroll(this::onScroll);

        widthProperty().addListener((obs, oldVal, newVal) -> {
            xOffset = (float) (getWidth() / 2F - canvas.getWidth() / 2F);
            centerCanvas();
            drawImage(current);
        });

        heightProperty().addListener((obs, oldVal, newVal) -> {
            yOffset = (float) (getHeight() / 2F - canvas.getHeight() / 2F);
            centerCanvas();
            drawImage(current);
        });

        setOnKeyPressed(keyEvent -> {
            if (keyEvent.isAltDown()) {
                renderCursor = false;
                setCursor(Cursor.CROSSHAIR);
                // Don't overwrite altStartColor unless starting ALT
                if (altStartColor == null) altStartColor = colorPicker.getValue();
                int px = (int) cursorX;
                int py = (int) cursorY;
                if (px >= 0 && px < current.getWidth() && py >= 0 && py < current.getHeight()) {
                    colorPicker.setValue(current.getPixelReader().getColor(px, py));
                }
                drawImage(current);
            }
        });


        // ALT released: restore previous color
        setOnKeyReleased(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.ALT) {
                if (altStartColor != null) colorPicker.setValue(altStartColor);
                altStartColor = null;
                renderCursor = true;
                drawImage(current);
                setCursor(Cursor.DEFAULT);
            }
        });
        setOnMouseDragged(this::onDrag);

        setOnMousePressed(mouseEvent -> {
            if (mouseEvent.isMiddleButtonDown() || (mouseEvent.isPrimaryButtonDown() && mouseEvent.isShiftDown())) {
                this.renderCursor = false;
                this.previousX = mouseEvent.getX();
                this.previousY = mouseEvent.getY();
                this.currentX = mouseEvent.getX();
                this.currentY = mouseEvent.getY();
            } else if (mouseEvent.isAltDown() && mouseEvent.isPrimaryButtonDown()) {
                // Eyedropper tool
               colorPicker.setValue(current.getPixelReader().getColor((int) cursorX, (int) cursorY));
               altStartColor = colorPicker.getValue();
            } else {
                drawOnCursor(mouseEvent);
            }
        });

        setOnMouseMoved(this::onMove);
        setOnMouseReleased(mouseEvent -> {
            this.renderCursor = true;
            this.setCursor(Cursor.DEFAULT);
        });
    }

    private void setPixelSafe(WritableImage image, int x, int y, Color color) {

        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        save.setText("Save *");
        if (x >= 0 && x < width && y >= 0 && y < height) {
            image.getPixelWriter().setColor(x, y, color);
        }
    }

    public void onMove(MouseEvent mouseEvent) {
        mouseX = mouseEvent.getX();
        mouseY = mouseEvent.getY();
        cursorX = (mouseX - xOffset) / scaleFactor;
        cursorY = (mouseY - yOffset) / scaleFactor;
        drawImage(current);
        if(mouseEvent.isAltDown()) {
            colorPicker.setValue(current.getPixelReader().getColor((int) cursorX, (int) cursorY));

        }
    }

    public void drawOnCursor(MouseEvent mouseEvent) {


        try {
            mouseX = mouseEvent.getX();
            mouseY = mouseEvent.getY();
            cursorX = (mouseX - xOffset) / scaleFactor;
            cursorY = (mouseY - yOffset) / scaleFactor;

            int px = (int) Math.floor(cursorX);
            int py = (int) Math.floor(cursorY);

            if (mouseEvent.isPrimaryButtonDown()) {
                setPixelSafe(current, px, py, colorPicker.getValue());
            }
            if (mouseEvent.isSecondaryButtonDown()) {
                renderCursor = false;
                setPixelSafe(current, px, py, new Color(0, 0, 0, 0));
            }
            drawImage(current);
        } catch (Exception ignored) {
        }
    }

    public void onDrag(MouseEvent mouseEvent) {
        if (mouseEvent.isMiddleButtonDown() || (mouseEvent.isPrimaryButtonDown() && mouseEvent.isShiftDown())) {
            this.setCursor(Cursor.OPEN_HAND);
            previousX = currentX;
            previousY = currentY;
            currentX = mouseEvent.getX();
            currentY = mouseEvent.getY();

            double dx = currentX - previousX;
            double dy = currentY - previousY;
            xOffset += (float) dx;
            yOffset += (float) dy;
            drawImage(current);
        } else {
            drawOnCursor(mouseEvent);

        }
    }

    public void onScroll(ScrollEvent scrollEvent) {
        if (scrollEvent.getDeltaY() > 0) {
            if (scaleFactor * current.getWidth() <= 16000) {
                scaleFactor *= 1.1f;
            }
        } else {
            scaleFactor /= 1.1f;
        }
        drawImage(current);
    }

    private void centerCanvas() {
        canvas.setTranslateX(xOffset);
        canvas.setTranslateY(yOffset);
    }

    public void drawImage(WritableImage image) {
        this.current = image;
        double imgWidth = image.getWidth();
        double imgHeight = image.getHeight();

        double canvasWidth = imgWidth * scaleFactor;
        double canvasHeight = imgHeight * scaleFactor;
        canvas.setWidth(canvasWidth);
        canvas.setHeight(canvasHeight);

        ctx.clearRect(0, 0, canvasWidth, canvasHeight);

        for (int x = 0; x < imgWidth; x++) {
            for (int y = 0; y < imgHeight; y++) {
                Color color = image.getPixelReader().getColor(x, y);
                if (Objects.equals(color, new Color(0.0D, 0.0D, 0.0D, 0.0D))) {
                    color = ((x + y) % 2 == 0) ? Color.rgb(128, 128, 128) : Color.rgb(192, 192, 192);
                }

                double drawX = Math.floor(x * scaleFactor);
                double drawY = Math.floor(y * scaleFactor);
                double drawW = Math.ceil(scaleFactor);
                double drawH = Math.ceil(scaleFactor);

                if (renderCursor && x == (int) cursorX && y == (int) cursorY) {
                    color = colorPicker.getValue();
                }

                ctx.setFill(color);
                ctx.fillRect(drawX, drawY, drawW, drawH);
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
        return "Image Editor";
    }
}
