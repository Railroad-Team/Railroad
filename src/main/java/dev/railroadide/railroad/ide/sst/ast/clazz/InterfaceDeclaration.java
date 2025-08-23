package dev.railroadide.railroad.ide.sst.ast.clazz;

import dev.railroadide.railroad.ide.sst.ast.AstKind;
import dev.railroadide.railroad.ide.sst.ast.AstNode;
import dev.railroadide.railroad.ide.sst.ast.AstVisitor;
import dev.railroadide.railroad.ide.sst.ast.Span;
import org.freedesktop.dbus.TypeRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record InterfaceDeclaration(
        Span span,
        List<Modifier> modifiers,
        List<Annotation> annotations,
        Name name,
        List<TypeParameter> typeParameters,
        List<TypeRef> extendsTypes,
        List<InterfaceMember> members) implements TypeDeclaration {
    @Override
    public AstKind kind() {
        return AstKind.INTERFACE_DECLARATION;
    }

    @Override
    public List<AstNode> children() {
        List<AstNode> children = new ArrayList<>();
        children.addAll(annotations);
        children.addAll(typeParameters);
        children.addAll(extendsTypes);
        children.addAll(members);
        return children;
    }

    @Override
    public <R> R accept(@NotNull AstVisitor<R> visitor) {
        return visitor.visitInterfaceDeclaration(this);
    }
}
