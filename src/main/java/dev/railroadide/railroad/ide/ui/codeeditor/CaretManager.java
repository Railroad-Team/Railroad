package dev.railroadide.railroad.ide.ui.codeeditor;

import javafx.beans.property.IntegerProperty;
import lombok.Getter;

import java.util.function.BiConsumer;

public class CaretManager {
    private final DocumentModel document;
    @Getter
    private final Caret caret;

    public CaretManager(DocumentModel document, Caret caret) {
        this.document = document;
        this.caret = caret;
    }

    public void setPosition(int line, int column) {
        int clampedLine = Math.max(0, Math.min(line, document.getLineCount() - 1));
        int clampedColumn = Math.max(0, Math.min(column, document.getLine(clampedLine).length()));
        this.caret.setLine(clampedLine);
        this.caret.setColumn(clampedColumn);
    }

    public void moveUp() {
        if (getLine() > 0) {
            decrement(caret.lineProperty());
        }
    }

    public void moveUpBy(int amount) {
        for (int i = 0; i < amount; i++) {
            moveUp();
        }
    }

    public void moveDown() {
        increment(caret.lineProperty());
    }

    public void moveDownBy(int amount) {
        for (int i = 0; i < amount; i++) {
            moveDown();
        }
    }

    public void moveLeft() {
        if (getColumn() > 0) {
            decrement(caret.columnProperty());
        } else if (caret.getLine() > 0) {
            decrement(caret.lineProperty());
            caret.setColumn(document.getLine(getLine()).length());
        }
    }

    public void moveLeftBy(int amount) {
        for (int i = 0; i < amount; i++) {
            moveLeft();
        }
    }

    public void moveRight() {
        if (getColumn() < document.getLine(getLine()).length()) {
            increment(caret.columnProperty());
        } else if (getLine() < document.getLineCount() - 1) {
            increment(caret.lineProperty());
            caret.setColumn(0);
        }
    }

    public void moveRightBy(int amount) {
        for (int i = 0; i < amount; i++) {
            moveRight();
        }
    }

    public int getLine() {
        return caret.getLine();
    }

    public int getColumn() {
        return caret.getColumn();
    }

    public void addLineListener(BiConsumer<Integer, Integer> listener) {
        this.caret.lineProperty().addListener((observable, oldValue, newValue) ->
                listener.accept(oldValue.intValue(), newValue.intValue()));
    }

    public void addColumnListener(BiConsumer<Integer, Integer> listener) {
        this.caret.columnProperty().addListener((observable, oldValue, newValue) ->
                listener.accept(oldValue.intValue(), newValue.intValue()));
    }

    public void addCaretListener(BiConsumer<Integer, Integer> listener) {
        this.caret.lineProperty().addListener((observable, oldValue, newValue) ->
                listener.accept(oldValue.intValue(), newValue.intValue()));
        this.caret.columnProperty().addListener((observable, oldValue, newValue) ->
                listener.accept(oldValue.intValue(), newValue.intValue()));
    }

    private static void decrement(IntegerProperty property) {
        property.set(Math.max(0, property.get() - 1));
    }

    private static void increment(IntegerProperty property) {
        property.set(property.get() + 1);
    }
}
