package dev.railroadide.core.ui.collage;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

import java.io.File;

public class GuiCollageElement implements CollageElement {
    private int x, y, width, height;
    private String resourceLocation;
    //TODO create enum types or something for different gui element types, all with a:
    // Size, Resource, options?

    public GuiCollageElement(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.resourceLocation = "D:\\item_slot.png";
    }

    @Override
    public int getX() {
        return x;
    }

    @Override
    public int getY() {
        return y;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void render(GraphicsContext gc) {
        gc.drawImage(new Image(new File(this.resourceLocation).toURI().toString()), getX(), getY());
    }

    public void setPosition(int deltaX, int deltaY) {
        this.x = deltaX;
        this.y = deltaY;
    }
}
