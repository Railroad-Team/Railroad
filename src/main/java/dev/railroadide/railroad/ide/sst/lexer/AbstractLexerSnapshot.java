package dev.railroadide.railroad.ide.sst.lexer;

import java.util.Objects;

public abstract class AbstractLexerSnapshot implements Lexer.Snapshot {
    public final int offset;
    public final int line;
    public final int column;
    public final int mode;

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
        if (o == null || getClass() != o.getClass()) return false;
        AbstractLexerSnapshot that = (AbstractLexerSnapshot) o;
        return offset == that.offset && line == that.line && column == that.column && mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(offset, line, column, mode);
    }
}
