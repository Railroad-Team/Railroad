package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.declarator.VariableDeclarator;
import dev.railroadide.railroad.ide.sst.ast.statements.block.BlockStatement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record CatchClause(
        Span span,
        VariableDeclarator exception,
        BlockStatement body
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.CATCH_CLAUSE;
    }

    @Override
    public List<AstNode> children() {
        return List.of(exception, body);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitCatchClause(this);
    }
}
