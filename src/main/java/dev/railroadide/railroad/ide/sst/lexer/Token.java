package dev.railroadide.railroad.ide.sst.lexer;

import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

public interface Token<T extends Enum<T>> {
    T type();

    String lexeme();

    int pos();

    int endOffset();

    int line();

    int column();

    TokenChannel channel();

    Set<TokenFlag> flags();

    default int length() {
        return endOffset() - pos();
    }

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
        public SimpleToken {
            if (lexeme == null)
                lexeme = "";

            if (channel == null)
                channel = TokenChannel.DEFAULT;

            if (flags == null)
                flags = EnumSet.noneOf(TokenFlag.class);
        }
    }

    record IgnoreToken<T extends Enum<T>>() implements Token<T> {
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
            return null;
        }

        @Override
        public Set<TokenFlag> flags() {
            return Set.of();
        }
    }

    record MissingToken<T extends Enum<T>>(
        T type,
        int pos,
        int line,
        int column
    ) implements Token<T> {
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
