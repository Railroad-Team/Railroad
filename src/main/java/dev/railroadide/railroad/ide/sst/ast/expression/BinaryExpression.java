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
 * An expression applying a binary operator to left and right operands.
 *
 * @param span source range occupied by this node
 * @param left left operand
 * @param operator operator token
 * @param right right operand
 */
public record BinaryExpression(
    Span span,
    Expression left,
    LexerToken<JavaTokenType> operator,
    Expression right
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.BINARY_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        return List.of(left, operator, right);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitBinaryExpression(this);
    }
}
