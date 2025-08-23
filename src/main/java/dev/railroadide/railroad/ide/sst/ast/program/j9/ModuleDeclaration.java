package dev.railroadide.railroad.ide.sst.ast.program.j9;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ModuleDeclaration(
        Span span,
        String name,
        boolean isOpen,
        boolean isAutomatic,
        RequiresDirective requiresDirective,
        ExportsDirective exportsDirective,
        OpensDirective opensDirective,
        UsesDirective usesDirective,
        ProvidesDirective providesDirective
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.MODULE_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        return List.of(requiresDirective, exportsDirective, opensDirective, usesDirective, providesDirective);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitModuleDeclaration(this);
    }
}
