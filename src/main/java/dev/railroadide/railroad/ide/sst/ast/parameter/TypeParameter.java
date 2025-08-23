package dev.railroadide.railroad.ide.sst.ast.parameter;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import org.freedesktop.dbus.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record TypeParameter(
        Span span,
        String name,
        List<Annotation> annotations,
        List<TypeRef> bounds) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.TYPE_PARAMETER;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(annotations);
        children.addAll(bounds);
        return List.copyOf(children);
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitTypeParameter(this);
    }
}
