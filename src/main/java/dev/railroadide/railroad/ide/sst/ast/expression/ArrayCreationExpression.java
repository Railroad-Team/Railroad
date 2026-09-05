package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.typeref.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An array allocation with dimension expressions and an optional initializer.
 *
 * @param span source range occupied by this node
 * @param type type reference used in the array creation
 * @param dimensions array length expressions in dimension order
 * @param initializer optional array initializer
 */
public record ArrayCreationExpression(
    Span span,
    TypeRef type,
    List<Expression> dimensions,
    Optional<ArrayInitializerExpression> initializer
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.ARRAY_CREATION_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.add(type);
        children.addAll(dimensions);
        initializer.ifPresent(children::add);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitArrayCreationExpression(this);
    }
}
