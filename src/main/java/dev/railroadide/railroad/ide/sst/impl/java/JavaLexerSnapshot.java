package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.lexer.AbstractLexerSnapshot;
import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.lexer.Token;

import java.util.Deque;
import java.util.List;
import java.util.Objects;

public class JavaLexerSnapshot extends AbstractLexerSnapshot {
    public final List<Lexer.LexError> diagnostics;
    public final Deque<Token<JavaTokenType>> lookaheadBuffer;

    public JavaLexerSnapshot(int offset, int line, int column, int mode, List<Lexer.LexError> diagnostics, Deque<Token<JavaTokenType>> lookaheadBuffer) {
        super(offset, line, column, mode);

        this.diagnostics = diagnostics;
        this.lookaheadBuffer = lookaheadBuffer;
    }

    @Override
    public String toString() {
        return "JavaLexerSnapshot{" +
                "offset=" + offset +
                ", line=" + line +
                ", column=" + column +
                ", mode=" + mode +
                ", diagnostics=" + diagnostics +
                ", lookaheadBuffer=" + lookaheadBuffer +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o) &&
                o instanceof JavaLexerSnapshot that &&
                diagnostics.equals(that.diagnostics) &&
                lookaheadBuffer.equals(that.lookaheadBuffer);
    }

    @Override
    public int hashCode() {
        return super.hashCode() + Objects.hash(diagnostics, lookaheadBuffer);
    }
}
