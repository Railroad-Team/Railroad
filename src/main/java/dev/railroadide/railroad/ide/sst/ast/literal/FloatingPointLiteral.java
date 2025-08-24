package dev.railroadide.railroad.ide.sst.ast.literal;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record FloatingPointLiteral(
        Span span,
        double value
) implements Literal {
    @Override
    public AstKind kind() {
        return AstKind.FLOATING_POINT_LITERAL;
    }

    @Override
    public List<AstNode> children() {
        return List.of();
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitFloatingPointLiteral(this);
    }
}
