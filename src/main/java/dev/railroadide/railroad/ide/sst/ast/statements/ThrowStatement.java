package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A statement that throws the exception produced by an expression.
 *
 * @param span source range occupied by this node
 * @param expression expression producing the exception to throw
 */
public record ThrowStatement(
    Span span,
    Expression expression
) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.THROW_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        return List.of(expression);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitThrowStatement(this);
    }
}
