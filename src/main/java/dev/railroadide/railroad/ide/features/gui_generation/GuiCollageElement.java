package dev.railroadide.railroad.ide.features.gui_generation;

import dev.railroadide.core.ui.collage.CollageElement;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

import java.io.File;

/**
 * A GUI element that can be added to a collage.
 * This element is used for inventory GUIs.
 */
public class GuiCollageElement implements CollageElement {
    private int x;
    private int y;
    private int width;
    private int height;
    private String resourceLocation;
    //TODO Make resource location configurable, and add support for options for the element.

    /**
     * Creates a new GuiCollageElement with the specified position and size.
     * @param x X position
     * @param y Y position
     * @param width Width
     * @param height Height
     */
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

    /**
     * Renders the GUI element on the provided GraphicsContext.
     * @param gc the GraphicsContext to render on
     */
    @Override
    public void render(GraphicsContext gc) {
        gc.drawImage(new Image(new File(this.resourceLocation).toURI().toString()), getX(), getY());
    }

    /**
     * Sets the position of the GUI element.
     * @param x X position
     * @param y Y position
     */
    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
