package dev.railroadide.railroad.ide.sst.ast.typeref;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record SugarTypeRef(
        Span span,
        List<Modifier> modifiers,
        List<Annotation> annotations,
        TypeRef baseType) implements TypeRef {

    @Override
    public AstKind kind() {
        return AstKind.SUGAR_TYPE;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(modifiers);
        children.addAll(annotations);
        children.add(baseType);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitSugarType(this);
    }
}
