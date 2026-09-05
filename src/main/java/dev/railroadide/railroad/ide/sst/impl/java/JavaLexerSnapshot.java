package dev.railroadide.railroad.ide.sst.impl.java;

import dev.railroadide.railroad.ide.sst.lexer.AbstractLexerSnapshot;
import dev.railroadide.railroad.ide.sst.lexer.Lexer;
import dev.railroadide.railroad.ide.sst.lexer.Token;

import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Stores a Java lexer's position and active mode together with its diagnostics and
 * lookahead buffer. The supplied collections are retained by reference rather than
 * copied, so subsequent collection changes are visible through this snapshot.
 */
public class JavaLexerSnapshot extends AbstractLexerSnapshot {
    /** Lexical diagnostics retained by this snapshot. */
    public final List<Lexer.LexError> diagnostics;
    /** Tokens already produced for lookahead, in consumption order. */
    public final Deque<Token<JavaTokenType>> lookaheadBuffer;

    /**
     * Creates a snapshot using the supplied lexer state and collection references.
     *
     * @param offset the zero-based character offset in the source
     * @param line the one-based source line
     * @param column the one-based source column
     * @param mode the active lexer mode
     * @param diagnostics the lexical diagnostics to retain by reference
     * @param lookaheadBuffer the pending lookahead tokens to retain by reference
     */
    public JavaLexerSnapshot(
        int offset,
        int line,
        int column,
        int mode,
        List<Lexer.LexError> diagnostics,
        Deque<Token<JavaTokenType>> lookaheadBuffer
    ) {
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
