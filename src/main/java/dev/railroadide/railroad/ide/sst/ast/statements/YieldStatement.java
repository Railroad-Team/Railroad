package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A statement supplying a result to an enclosing switch expression.
 *
 * @param span source range occupied by this node
 * @param value expression producing the switch result
 */
public record YieldStatement(Span span, Expression value) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.YIELD_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        return List.of(value);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitYieldStatement(this);
    }
}
