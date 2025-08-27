package dev.railroadide.core.ui.collage;

import javafx.scene.canvas.GraphicsContext;

/**
 * A collage element that can be rendered on a canvas.
 */
public interface CollageElement {
    int getX();
    int getY();
    int getWidth();
    int getHeight();

    /**
     * Renders the element on the given GraphicsContext.
     * @param gc the GraphicsContext to render on
     */
    void render(GraphicsContext gc);
}
