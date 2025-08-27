package dev.railroadide.core.ui.collage;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A canvas that holds collage elements and manages their state.
 * @param <T> the type of collage element
 */
public abstract class CollageCanvas<T extends CollageElement> {
    private final List<CanvasChangeListener> listeners = new ArrayList<>();
    private List<T> elements;
    private T selectedElement;

    /**
     * Creates a new CollageCanvas with an empty list of elements.
     */
    public CollageCanvas() {
        this.elements = new ArrayList<>();
    }

    /**
     * Adds an element to the canvas and notifies listeners of the change.
     * @param element the element to add
     */
    public void addElement(T element) {
        this.elements.add(element);
        notifyChangeListeners();
    }

    /**
     * Removes an element from the canvas and notifies listeners of the change.
     * @param element the element to remove
     */
    public void removeElement(T element) {
        this.elements.remove(element);
        notifyChangeListeners();
    }

    /**
     * Updates an existing element on the canvas and notifies listeners of the change.
     * @param oldElement the element to update
     * @param newElement the new element to replace the old one
     */
    public void updateElement(T oldElement, T newElement) {
        int index = elements.indexOf(oldElement);
        if (index != -1) {
            elements.set(index, newElement);
        }
        notifyChangeListeners();
    }

    /**
     * Gets an unmodifiable list of elements on the canvas.
     * @return the list of elements
     */
    public List<T> getElements() {
        return Collections.unmodifiableList(elements);
    }

    /**
     * Clears all elements from the canvas and notifies listeners of the change.
     */
    public void clear() {
        this.elements.clear();
        this.selectedElement = null;
        notifyChangeListeners();
    }

    /**
     * Adds a listener to be notified when the canvas changes.
     * @param listener the listener to add
     */
    public void addChangeListener(CanvasChangeListener listener) {
        this.listeners.add(listener);
    }

    /**
     * Removes a listener from the canvas.
     * @param listener the listener to remove
     */
    public void removeChangeListener(CanvasChangeListener listener) {
        this.listeners.remove(listener);
    }

    /**
     * Notifies all registered listeners of a change to the canvas.
     */
    public void notifyChangeListeners() {
        for (CanvasChangeListener listener : listeners) {
            listener.onCanvasChanged();
        }
    }

    /**
     * Sets the selected element on the canvas and notifies listeners of the change.
     * @param element the element to select
     */
    public void setSelectedElement(T element) {
        this.selectedElement = element;
        notifyChangeListeners();
    }

    /**
     * Gets the currently selected element on the canvas.
     * @return an Optional containing the selected element, or empty if no element is selected
     */
    public Optional<T> getSelectedElement() {
        return Optional.ofNullable(this.selectedElement);
    }

    /**
     * Gets the element at the specified coordinates, if any.
     * @param x the x coordinate
     * @param y the y coordinate
     * @return an Optional containing the element at the specified coordinates, or empty if no element is found
     */
    public Optional<T> getElementFromPosition(int x, int y) {
        for (T element : elements) {
            if (x < element.getX() || x > element.getX() + element.getWidth()) continue;
            if (y < element.getY() || y > element.getY() + element.getHeight()) continue;
            return Optional.of(element);
        }

        return Optional.empty();
    }

    /**
     * Renders the selection box around the selected element, if any.
     * @param gc the GraphicsContext to render with
     */
    public void render(GraphicsContext gc) {
        if (getSelectedElement().isPresent()) {
            T selected = getSelectedElement().get();
            var box = new Rectangle(selected.getX(), selected.getY(), selected.getWidth(), selected.getHeight());
            renderSelectionBox(gc, box);
        }
    }

    /**
     * Renders a selection box around the given rectangle.
     * @param gc the GraphicsContext to render with
     * @param box the rectangle to draw the selection box around
     */
    void renderSelectionBox(GraphicsContext gc, Rectangle box) {
        gc.setStroke(Color.color(0.23, 1, 0.34)); // TODO Use theme colour?
        gc.setLineWidth(3);
        final double x = box.getX() - gc.getLineWidth() + 1;
        final double y = box.getY() - gc.getLineWidth() + 1;
        final double w = box.getWidth() + gc.getLineWidth();
        final double h = box.getHeight() + gc.getLineWidth();
        gc.strokeRect(x, y, w, h);
    }
}
