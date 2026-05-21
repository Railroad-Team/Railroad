package dev.railroadide.railroad.ide.sst.ast;

/**
 * Source span for an AST node.
 *
 * @param pos zero-based inclusive start offset
 * @param endPos zero-based exclusive end offset
 * @param line one-based line number for {@code pos}
 * @param column one-based column number for {@code pos}
 */
public record Span(int pos, int endPos, int line, int column) {
    public Span {
        if (pos < 0 || endPos < pos || line < 1 || column < 1)
            throw new IllegalArgumentException("Invalid span parameters: pos=" + pos + ", endPos=" + endPos + ", line=" + line + ", column=" + column);
    }
}
