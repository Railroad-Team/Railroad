package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record FieldAccessExpression(
        Span span,
        Expression expression,
        String name
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
