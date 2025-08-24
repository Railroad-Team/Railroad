package dev.railroadide.railroad.ide.sst.ast.program.j9;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record ProvidesDirective(
        Span span,
        Name service,
        List<Name> implementations
) implements ModuleDirective {
    @Override
    public AstKind kind() {
        return AstKind.PROVIDES_DIRECTIVE;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.add(service);
        children.addAll(implementations);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitProvidesDirective(this);
    }
}
