package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record WhileStatement(Span span, Expression condition, Statement body) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.WHILE_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        return List.of(condition, body);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitWhileStatement(this);
    }
}
