package dev.railroadide.railroad.ide.sst.ast.program.j9;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record UsesDirective(
        Span span,
        String moduleName,
        boolean isStatic
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.USES_DIRECTIVE;
    }

    @Override
    public List<AstNode> children() {
        return List.of(); // Uses directives do not have children nodes.
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitUsesDirective(this);
    }
}
