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
 * An assertion condition with an optional diagnostic detail expression.
 *
 * @param span source range occupied by this node
 * @param condition condition expression
 * @param message optional assertion detail expression
 */
public record AssertStatement(
    Span span,
    Expression condition,
    Optional<Expression> message
) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.ASSERT_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.add(condition);
        message.ifPresent(children::add);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitAssertStatement(this);
    }
}
