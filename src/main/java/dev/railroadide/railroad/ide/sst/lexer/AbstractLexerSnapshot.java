package dev.railroadide.railroad.ide.sst.lexer;

import java.util.Objects;

/** Base lexer snapshot storing a source position and active lexical mode. */
public abstract class AbstractLexerSnapshot implements Lexer.Snapshot {
    /** Zero-based character offset of the input cursor. */
    public final int offset;
    /** One-based source line of the input cursor. */
    public final int line;
    /** One-based source column of the input cursor. */
    public final int column;
    /** Implementation-specific active mode identifier. */
    public final int mode;

    /**
     * Stores the supplied source position and mode.
     *
     * @param offset the zero-based character offset
     * @param line the one-based source line
     * @param column the one-based source column
     * @param mode the active lexical mode identifier
     */
    public AbstractLexerSnapshot(int offset, int line, int column, int mode) {
        this.offset = offset;
        this.line = line;
        this.column = column;
        this.mode = mode;
    }

    @Override
    public String toString() {
        return "AbstractLexerSnapshot{" +
            "offset=" + offset +
            ", line=" + line +
            ", column=" + column +
            ", mode=" + mode +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        AbstractLexerSnapshot that = (AbstractLexerSnapshot) o;
        return offset == that.offset && line == that.line && column == that.column && mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, line, column, mode);
    }
}
