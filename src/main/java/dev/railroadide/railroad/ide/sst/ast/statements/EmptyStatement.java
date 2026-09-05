package dev.railroadide.railroad.ide.sst.ast.statements;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An empty statement represented by a semicolon.
 *
 * @param span source range occupied by this node
 */
public record EmptyStatement(Span span) implements Statement {
    @Override
    public AstKind kind() {
        return AstKind.EMPTY_STATEMENT;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitEmptyStatement(this);
    }
}
