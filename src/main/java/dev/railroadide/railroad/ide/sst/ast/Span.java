package dev.railroadide.railroad.ide.sst.ast;

public record Span(int pos, int endPos, int line, int column) {
    public Span {
        if (pos < 0 || endPos < pos || line < 1 || column < 1)
            throw new IllegalArgumentException("Invalid span parameters: pos=" + pos + ", endPos=" + endPos + ", line=" + line + ", column=" + column);
    }
}
