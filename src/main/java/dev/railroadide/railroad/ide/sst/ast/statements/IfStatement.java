package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A conditional statement with a then branch and an optional else branch.
 *
 * @param span source range occupied by this node
 * @param condition condition expression
 * @param thenStatement statement executed when the condition is true
 * @param elseStatement optional statement executed when the condition is false
 */
public record IfStatement(
    Span span,
    Expression condition,
    Statement thenStatement,
    Optional<Statement> elseStatement
) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.IF_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.add(condition);
        children.add(thenStatement);
        elseStatement.ifPresent(children::add);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitIfStatement(this);
    }
}
