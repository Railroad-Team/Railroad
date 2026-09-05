package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An ordered sequence of expressions initializing an array.
 *
 * @param span source range occupied by this node
 * @param values element values in source order
 */
public record ArrayInitializerExpression(
    Span span,
    List<Expression> values
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.ARRAY_INITIALIZER;
    }

    @Override
    public List<AstNode> children() {
        return List.copyOf(values);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitArrayInitializer(this);
    }
}
