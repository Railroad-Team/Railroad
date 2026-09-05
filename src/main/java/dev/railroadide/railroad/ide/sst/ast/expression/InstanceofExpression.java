package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.Pattern;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An {@code instanceof} test of an expression against a type or deconstruction pattern.
 *
 * @param span source range occupied by this node
 * @param expression value tested against the pattern
 * @param pattern pattern to match
 */
public record InstanceofExpression(
    Span span,
    Expression expression,
    Pattern pattern
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.INSTANCEOF_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        return List.of(expression, pattern);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitInstanceofExpression(this);
    }
}
