package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.expression.Expression;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record SynchronizedStatement(
        Span span,
        Expression expression,
        BlockStatement body
) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.SYNCHRONIZED_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        return List.of(expression, body);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitSynchronizedStatement(this);
    }
}
