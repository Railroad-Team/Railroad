package dev.railroadide.railroad.ide.sst.lexer;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/**
 * A language token with its source text, position, channel, and lexical flags.
 *
 * @param <T> the language-specific token kind enum
 */
public interface Token<T extends Enum<T>> {
    /**
     * Returns the language-specific token kind.
     *
     * @return the token kind, or {@code null} for an ignore token
     */
    T type();

    /**
     * Returns the token's source spelling.
     *
     * @return the lexeme, which may be empty for generated or EOF tokens
     */
    String lexeme();

    /**
     * Returns the token's starting input offset.
     *
     * @return the inclusive zero-based start offset
     */
    int pos();

    /**
     * Returns the offset immediately after the token.
     *
     * @return the exclusive zero-based end offset
     */
    int endOffset();

    /**
     * Returns the source line at the token's start.
     *
     * @return the one-based line, or zero for an ignore token
     */
    int line();

    /**
     * Returns the source column at the token's start.
     *
     * @return the one-based column, or zero for an ignore token
     */
    int column();

    /**
     * Returns the processing channel containing this token.
     *
     * @return the token channel
     */
    TokenChannel channel();

    /**
     * Returns additional properties of the token.
     *
     * @return the token's lexical flags
     */
    Set<TokenFlag> flags();

    /**
     * Computes the length of the source span occupied by this token.
     *
     * @return the end offset minus the start offset
     */
    default int length() {
        return endOffset() - pos();
    }

    /**
     * Stores a token's values, supplying defaults for null text, channel, and flags.
     *
     * @param <T> the token kind enum
     * @param type the language-specific token kind
     * @param lexeme the source spelling, or {@code null} for an empty lexeme
     * @param pos the inclusive zero-based start offset
     * @param endOffset the exclusive zero-based end offset
     * @param line the one-based starting line
     * @param column the one-based starting column
     * @param channel the token channel, or {@code null} for the default channel
     * @param flags the lexical flags, or {@code null} for no flags; retained by reference
     */
    record SimpleToken<T extends Enum<T>>(
        T type,
        String lexeme,
        int pos,
        int endOffset,
        int line,
        int column,
        TokenChannel channel,
        Set<TokenFlag> flags
    ) implements Token<T> {
        /**
         * Creates a token, normalizing null optional values to their defaults.
         *
         * @param type the language-specific token kind
         * @param lexeme the source spelling, or {@code null} for empty text
         * @param pos the inclusive zero-based start offset
         * @param endOffset the exclusive zero-based end offset
         * @param line the one-based starting line
         * @param column the one-based starting column
         * @param channel the channel, or {@code null} for the default channel
         * @param flags the flags, or {@code null} for no flags
         */
        public SimpleToken {
            if (lexeme == null) {
                lexeme = "";
            }

            if (channel == null) {
                channel = TokenChannel.DEFAULT;
            }

            if (flags == null) {
                flags = EnumSet.noneOf(TokenFlag.class);
            }
        }
    }

    /**
     * A zero-width trivia marker emitted for lexer transitions that produce no source token.
     *
     * @param <T> the token kind enum
     */
    record IgnoreToken<T extends Enum<T>>() implements Token<T> {
        /** Creates an empty ignore marker. */
        public IgnoreToken {
        }

        @Override
        public T type() {
            return null;
        }

        @Override
        public String lexeme() {
            return "";
        }

        @Override
        public int pos() {
            return 0;
        }

        @Override
        public int endOffset() {
            return 0;
        }

        @Override
        public int line() {
            return 0;
        }

        @Override
        public int column() {
            return 0;
        }

        @Override
        public TokenChannel channel() {
            return TokenChannel.TRIVIA;
        }

        @Override
        public Set<TokenFlag> flags() {
            return Set.of();
        }
    }

    /**
     * A zero-width placeholder for an expected token absent from the source.
     *
     * @param <T> the token kind enum
     * @param type the expected token kind
     * @param pos the zero-based insertion offset
     * @param line the one-based insertion line
     * @param column the one-based insertion column
     */
    record MissingToken<T extends Enum<T>>(
        T type,
        int pos,
        int line,
        int column
    ) implements Token<T> {
        /**
         * Creates a missing token at another token's starting position.
         *
         * @param type the expected token kind
         * @param token the token supplying the insertion position
         */
        public MissingToken(T type, Token<T> token) {
            this(type, token.pos(), token.line(), token.column());
        }

        @Override
        public String lexeme() {
            return "";
        }

        @Override
        public int endOffset() {
            return pos;
        }

        @Override
        public TokenChannel channel() {
            return TokenChannel.DEFAULT;
        }

        @Override
        public Set<TokenFlag> flags() {
            return EnumSet.noneOf(TokenFlag.class);
        }

        @Override
        public @NotNull String toString() {
            return "<Missing %s(%s, L%s:%s)>".formatted(type, pos, line, column);
        }
    }
}
