package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record LexerToken<T extends Enum<T>>(Span span, T tokenType, String text) implements AstNode {
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
