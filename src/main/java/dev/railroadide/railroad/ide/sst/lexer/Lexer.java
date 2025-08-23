package dev.railroadide.railroad.ide.sst.lexer;

import java.io.Closeable;
import java.util.List;
import java.util.Optional;

public interface Lexer<T extends Enum<T>> extends Closeable {
    Token<T> nextToken();

    Token<T> lookahead(int k);

    default boolean isAtEnd() {
        return isNextTokenType(TokenChannel.DEFAULT, TokenFlag.EOF);
    }

    default boolean isNextTokenType(T type) {
        return lookahead(1).type() == type;
    }

    default boolean isNextTokenType(TokenChannel channel, TokenFlag flag) {
        Token<T> token = lookahead(1);
        return token.channel() == channel && token.flags().contains(flag);
    }

    default void unread(Token<T> token) {
        throw new UnsupportedOperationException("Unread operation is not supported by this lexer.");
    }

    int offset();

    int line();

    int column();

    Optional<Integer> totalLength();

    Snapshot snapshot();

    void restore(Snapshot snapshot);

    int mode();

    int pushMode(int mode);

    int popMode();

    List<LexError> diagnostics();

    Optional<String> sourceId();

    @Override
    default void close() {
        // Default implementation does nothing.
    }

    interface Snapshot {}

    record LexError(String message, int offset, int line, int column) {
        public LexError(String message, int offset) {
            this(message, offset, -1, -1);
        }
    }
}
