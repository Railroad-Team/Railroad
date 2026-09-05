package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A loop that evaluates its condition after executing its body.
 *
 * @param span source range occupied by this node
 * @param body statement executed before testing the condition
 * @param condition condition expression
 */
public record DoWhileStatement(Span span, Statement body, Expression condition) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.DO_WHILE_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        return List.of(body, condition);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitDoWhileStatement(this);
    }
}
