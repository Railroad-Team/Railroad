package dev.railroadide.railroad.ide.ui.codeeditor;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.util.function.BiConsumer;

public class CaretManager {
    private final DocumentModel document;
    private final IntegerProperty line = new SimpleIntegerProperty(0);
    private final IntegerProperty column = new SimpleIntegerProperty(0);

    public CaretManager(DocumentModel document) {
        this.document = document;
    }

    public void setPosition(int line, int column) {
        int clampedLine = Math.max(0, Math.min(line, document.getLineCount() - 1));
        int clampedColumn = Math.max(0, Math.min(column, document.getLine(clampedLine).length()));
        this.line.set(clampedLine);
        this.column.set(clampedColumn);
    }

    public void moveUp() {
        if (line.get() > 0) {
            decrement(line);
        }
    }

    public void moveUpBy(int amount) {
        for (int i = 0; i < amount; i++) {
            moveUp();
        }
    }

    public void moveDown() {
        increment(line);
    }

    public void moveDownBy(int amount) {
        for (int i = 0; i < amount; i++) {
            moveDown();
        }
    }

    public void moveLeft() {
        if (column.get() > 0) {
            decrement(column);
        } else if (line.get() > 0) {
            line.set(line.get() - 1);
            column.set(document.getLine(line.get()).length());
        }
    }

    public void moveLeftBy(int amount) {
        for (int i = 0; i < amount; i++) {
            moveLeft();
        }
    }

    public void moveRight() {
        if (column.get() < document.getLine(line.get()).length()) {
            increment(column);
        } else if (line.get() < document.getLineCount() - 1) {
            line.set(line.get() + 1);
            column.set(0);
        }
    }

    public void moveRightBy(int amount) {
        for (int i = 0; i < amount; i++) {
            moveRight();
        }
    }

    public int getLine() {
        return line.get();
    }

    public int getColumn() {
        return column.get();
    }

    public void addLineListener(BiConsumer<Integer, Integer> listener) {
        line.addListener((observable, oldValue, newValue) ->
                listener.accept(oldValue.intValue(), newValue.intValue()));
    }

    public void addColumnListener(BiConsumer<Integer, Integer> listener) {
        column.addListener((observable, oldValue, newValue) ->
                listener.accept(oldValue.intValue(), newValue.intValue()));
    }

    public void addCaretListener(BiConsumer<Integer, Integer> listener) {
        line.addListener((observable, oldValue, newValue) ->
                listener.accept(oldValue.intValue(), newValue.intValue()));
        column.addListener((observable, oldValue, newValue) ->
                listener.accept(oldValue.intValue(), newValue.intValue()));
    }

    private static void decrement(IntegerProperty property) {
        property.set(Math.max(0, property.get() - 1));
    }

    private static void increment(IntegerProperty property) {
        property.set(property.get() + 1);
    }
}
