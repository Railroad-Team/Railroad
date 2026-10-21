package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An AST leaf preserving a lexer token and its source range.
 *
 * @param <T> enum type identifying lexical token kinds
 * @param span source range occupied by this node
 * @param tokenType lexical token kind
 * @param text token text
 */
public record LexerToken<T extends Enum<T>>(Span span, T tokenType, String text) implements AstNode {
    /**
     * Wraps a lexer token as an AST leaf using the supplied span.
     *
     * @param <T> enum type identifying lexical token kinds
     * @param span source range occupied by this node
     * @param token lexer token to wrap
     * @return new AST token carrying the token kind and lexeme
     */
    public static <T extends Enum<T>> LexerToken<T> of(Span span, Token<T> token) {
        return new LexerToken<>(span, token.type(), token.lexeme());
    }

    @Override
    public AstKind kind() {
        return AstKind.TOKEN;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitToken(this);
    }
}
