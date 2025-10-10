package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.LexerToken;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import dev.railroadide.railroad.ide.sst.lexer.Token;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record UnaryExpression(
        Span span,
        LexerToken<JavaTokenType> operator,
        Expression expression,
        boolean isPrefix
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.BINARY_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        return isPrefix ? List.of(operator, expression) : List.of(expression, operator);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitUnaryExpression(this);
    }
}
