package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A cast to a target type with optional additional intersection bounds.
 *
 * @param span source range occupied by this node
 * @param target target type of the cast
 * @param additionalBounds additional intersection bounds of the cast
 * @param expression expression whose value is cast
 */
public record TypeCastExpression(
    Span span,
    TypeRef target,
    List<TypeRef> additionalBounds,
    Expression expression
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.TYPE_CAST_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        return List.of(target, expression);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitTypeCastExpression(this);
    }
}
