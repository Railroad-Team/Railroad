package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An expression evaluated as a standalone statement.
 *
 * @param span source range occupied by this node
 * @param expression expression evaluated by the statement
 */
public record ExpressionStatement(Span span, Expression expression) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.EXPRESSION_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        return List.of(expression);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitExpressionStatement(this);
    }
}
