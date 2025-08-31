package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.ElementValue;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ConditionalExpression(
        Span span,
        Expression condition,
        Expression trueExpression,
        Expression falseExpression
) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.CONDITIONAL_EXPRESSION;
    }

    @Override
    public List<AstNode> children() {
        return List.of(condition, trueExpression, falseExpression);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitConditionalExpression(this);
    }
}
