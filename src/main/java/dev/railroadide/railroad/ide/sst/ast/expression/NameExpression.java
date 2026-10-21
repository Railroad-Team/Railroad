package dev.railroadide.railroad.ide.sst.ast.expression;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A simple or qualified name stored as ordered identifier segments.
 *
 * @param span source range occupied by this node
 * @param parts name segments in source order
 */
public record NameExpression(Span span, List<String> parts) implements Expression {
    @Override
    public AstKind kind() {
        return AstKind.NAME;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitName(this);
    }
}
