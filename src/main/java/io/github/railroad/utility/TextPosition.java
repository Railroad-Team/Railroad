package io.github.railroad.utility;

public class TextPosition {
    public int x;
    public int y;
    public TextPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof TextPosition textPosition) {
            return textPosition.x == x && textPosition.y == y;
        }
        else {
            return false;
        }
    }
}
