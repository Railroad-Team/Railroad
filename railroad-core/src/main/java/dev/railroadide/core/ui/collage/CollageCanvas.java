package dev.railroadide.core.ui.collage;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.awt.*;
import java.util.*;
import java.util.List;

abstract class CollageCanvas<T extends CollageElement> {
    private List<T> elements;
    private T selectedElement;

    public CollageCanvas() {
        this.elements = new ArrayList<>();
    }

    public void addElement(T element) {
        this.elements.add(element);
    }

    public void removeElement(T element) {
        this.elements.remove(element);
    }

    public void updateElement(T oldElement, T newElement) {
        int index = elements.indexOf(oldElement);
        if (index != -1) {
            elements.set(index, newElement);
        }
    }

    public List<T> getElements() {
        return Collections.unmodifiableList(elements);
    }

    public void clear() {
        this.elements.clear();
    }

    public void setSelectedElement(T element) {
        this.selectedElement = element;
    }

    public Optional<T> getSelectedElement() {
        return Optional.ofNullable(this.selectedElement);
    }

    public Optional<T> getClickedElement(int x, int y) {
        for (T element : elements) {
            if (x < element.getX() || x > element.getX() + element.getWidth()) continue;
            if (y < element.getY() || y > element.getY() + element.getHeight()) continue;
            return Optional.of(element);
        }

        return Optional.empty();
    }

    void render(GraphicsContext gc) {
        if (getSelectedElement().isPresent()) {// TODO renderSelectedBox
            T selected = getSelectedElement().get();
            gc.setStroke(Color.color(1, 0, 0));
            gc.setLineWidth(3);
            //TODO render equal sizes on each side
            gc.strokeRect(selected.getX() - 1, selected.getY() - 1, selected.getWidth() + gc.getLineWidth() + 1, selected.getHeight() + gc.getLineWidth() + 1);
        }
    };
}
