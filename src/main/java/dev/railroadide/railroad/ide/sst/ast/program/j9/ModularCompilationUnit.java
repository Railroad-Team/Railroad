package dev.railroadide.railroad.ide.sst.ast.program.j9;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record ModularCompilationUnit(
        Span span,
        boolean isOpen,
        Name name,
        List<Annotation> annotations,
        List<ModuleDirective> directives
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.MODULAR_COMPILATION_UNIT;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.add(name);
        children.addAll(annotations);
        children.addAll(directives);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitModularCompilationUnit(this);
    }
}
