package dev.railroadide.railroad.ide.sst.ast.statements.block;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.LambdaBody;
import dev.railroadide.railroad.ide.sst.ast.statements.Statement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record BlockStatement(Span span, List<Statement> statements) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.BLOCK_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        return List.copyOf(statements);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitBlockStatement(this);
    }
}
