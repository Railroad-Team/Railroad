package dev.railroadide.railroad.ide.sst.ast.program;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.clazz.TypeDeclaration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public record CompilationUnit(
        Span span,
        Optional<PackageDeclaration> packageDeclaration,
        List<ImportDeclaration> importDeclarations,
        List<TypeDeclaration> typeDeclarations
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.COMPILATION_UNIT;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        packageDeclaration.ifPresent(children::add);
        children.addAll(importDeclarations);
        children.addAll(typeDeclarations);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitCompilationUnit(this);
    }
}
