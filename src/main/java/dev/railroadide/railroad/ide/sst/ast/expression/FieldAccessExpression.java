package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A named field selected from a qualifying expression.
 *
 * @param span source range occupied by this node
 * @param expression qualifying expression whose field is selected
 * @param name selected field name
 */
public record FieldAccessExpression(
    Span span,
    Expression expression,
    NameExpression name
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.FIELD_ACCESS_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        return List.of(expression);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitFieldAccessExpression(this);
    }
}
