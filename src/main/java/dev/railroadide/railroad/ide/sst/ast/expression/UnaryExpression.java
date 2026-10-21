package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.LexerToken;
import dev.railroadide.railroad.ide.sst.impl.java.JavaTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A prefix or postfix operator applied to a single operand.
 *
 * @param span source range occupied by this node
 * @param operator operator token
 * @param expression operand of the unary operator
 * @param isPrefix whether the operator precedes its operand
 */
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
