package dev.railroadide.railroad.ide.sst.ast.program.j9;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record RequiresDirective(
        Span span,
        boolean isStatic,
        boolean isTransitive,
        Name moduleName
) implements ModuleDirective {
    @Override
    public AstKind kind() {
        return AstKind.REQUIRES_DIRECTIVE;
    }

    @Override
    public List<AstNode> children() {
        return List.of(moduleName);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitRequiresDirective(this);
    }
}
