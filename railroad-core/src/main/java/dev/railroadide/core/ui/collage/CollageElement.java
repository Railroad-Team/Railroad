package dev.railroadide.core.ui.collage;

import javafx.scene.canvas.GraphicsContext;

public interface CollageElement {
    int getX();
    int getY();
    int getWidth();
    int getHeight();

    public abstract void render(GraphicsContext gc);
}
