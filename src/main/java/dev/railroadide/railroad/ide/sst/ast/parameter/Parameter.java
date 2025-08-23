package dev.railroadide.railroad.ide.sst.ast.parameter;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import dev.railroadide.railroad.ide.sst.ast.annotation.Annotation;
import dev.railroadide.railroad.ide.sst.ast.generic.Modifier;
import dev.railroadide.railroad.ide.sst.ast.generic.Name;
import org.freedesktop.dbus.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record Parameter(
        Span span,
        List<Modifier> modifiers,
        List<Annotation> annotations,
        TypeRef type,
        boolean isVarArgs,
        Name name
) implements AstNode {
    @Override
    public AstKind kind() {
        return AstKind.PARAMETER;
    }

    @Override
    public List<AstNode> children() {
        return List.of(
                type,
                name
        );
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitParameter(this);
    }
}
