package dev.railroadide.railroad.ide.sst.ast.generic;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A declaration modifier keyword and its source location.
 *
 * @param span source range occupied by this node
 * @param name modifier keyword spelling
 */
public record Modifier(Span span, String name) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.MODIFIER;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitModifier(this);
    }
}
