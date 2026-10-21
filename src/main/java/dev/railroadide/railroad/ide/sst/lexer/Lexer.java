package dev.railroadide.railroad.ide.sst.lexer;

import java.io.Closeable;
import java.util.List;
import java.util.Optional;

/**
 * Produces positioned tokens and supports lookahead, diagnostics, and restorable lexer state.
 *
 * @param <T> the language-specific token kind enum
 */
public interface Lexer<T extends Enum<T>> extends Closeable {
    /**
     * Consumes the next token from the input or lookahead buffer.
     *
     * @return the next token, including an EOF token at the end of input
     */
    Token<T> nextToken();

    /**
     * Reads ahead without consuming tokens from the caller's token stream.
     *
     * @param k the one-based token distance
     * @return the token at the requested distance
     */
    Token<T> lookahead(int k);

    /**
     * Checks whether the next token marks the end of input on the default channel.
     *
     * @return whether the next token is an EOF token
     */
    default boolean isAtEnd() {
        return isNextTokenType(TokenChannel.DEFAULT, TokenFlag.EOF);
    }

    /**
     * Checks the next token's language-specific kind.
     *
     * @param type the expected token kind
     * @return whether the next token has that kind
     */
    default boolean isNextTokenType(T type) {
        return lookahead(1).type() == type;
    }

    /**
     * Checks the next token's channel and flags.
     *
     * @param channel the required channel
     * @param flag the required flag
     * @return whether the next token matches both requirements
     */
    default boolean isNextTokenType(TokenChannel channel, TokenFlag flag) {
        Token<T> token = lookahead(1);
        return token.channel() == channel && token.flags().contains(flag);
    }

    /**
     * Pushes a token back for subsequent consumption, if the implementation supports it.
     *
     * @param token the token to return to the stream
     * @throws UnsupportedOperationException if unread is unsupported, as in the default implementation
     */
    default void unread(Token<T> token) {
        throw new UnsupportedOperationException("Unread operation is not supported by this lexer.");
    }

    /**
     * Returns the current input cursor offset, which may include buffered lookahead.
     *
     * @return the zero-based character offset
     */
    int offset();

    /**
     * Returns the current input cursor line.
     *
     * @return the one-based source line
     */
    int line();

    /**
     * Returns the current input cursor column.
     *
     * @return the one-based source column
     */
    int column();

    /**
     * Reports the input length when known.
     *
     * @return the character count, or an empty optional for an unknown length
     */
    Optional<Integer> totalLength();

    /**
     * Captures implementation-specific state for a later restore.
     *
     * @return the captured lexer state
     */
    Snapshot snapshot();

    /**
     * Restores the lexer from a compatible state snapshot.
     *
     * @param snapshot the state to restore
     */
    void restore(Snapshot snapshot);

    /**
     * Returns the active lexical mode.
     *
     * @return the implementation-specific mode identifier
     */
    int mode();

    /**
     * Pushes a lexical mode onto the mode stack.
     *
     * @param mode the mode to activate
     * @return the previously active mode
     */
    int pushMode(int mode);

    /**
     * Restores the previous lexical mode, retaining the base mode when necessary.
     *
     * @return the mode active after the operation
     */
    int popMode();

    /**
     * Returns lexical errors collected while producing tokens.
     *
     * @return the collected diagnostics
     */
    List<LexError> diagnostics();

    /**
     * Returns the identifier associated with the source input.
     *
     * @return the source identifier, or an empty optional if unspecified
     */
    Optional<String> sourceId();

    @Override
    default void close() {
        // Default implementation does nothing.
    }

    /** Marker for implementation-specific restorable lexer state. */
    interface Snapshot {
    }

    /**
     * Describes a lexical error and its location in the source.
     *
     * @param message the diagnostic text
     * @param offset the zero-based character offset
     * @param line the one-based line, or -1 when unknown
     * @param column the one-based column, or -1 when unknown
     */
    record LexError(String message, int offset, int line, int column) {
        /**
         * Creates an error whose line and column are unknown.
         *
         * @param message the diagnostic text
         * @param offset the zero-based character offset
         */
        public LexError(String message, int offset) {
            this(message, offset, -1, -1);
        }
    }
}
